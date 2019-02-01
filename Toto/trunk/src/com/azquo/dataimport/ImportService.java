package com.azquo.dataimport;


import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;
import org.zkoss.poi.ss.usermodel.BuiltinFormats;
import org.zkoss.poi.ss.usermodel.DataFormatter;
import org.zkoss.poi.xssf.model.StylesTable;
import com.azquo.StringLiterals;
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
import groovy.lang.GroovyShell;
import groovy.lang.Script;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.math.NumberUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.*;
import org.zeroturnaround.zip.ZipUtil;
import org.zkoss.poi.hssf.usermodel.HSSFWorkbook;
import org.zkoss.poi.openxml4j.opc.OPCPackage;
import org.zkoss.poi.ss.format.CellDateFormatter;
import org.zkoss.poi.ss.usermodel.*;
import org.zkoss.poi.ss.util.AreaReference;
import org.zkoss.poi.ss.util.CellRangeAddress;
import org.zkoss.poi.ss.util.CellReference;
import org.zkoss.poi.xssf.eventusermodel.ReadOnlySharedStringsTable;
import org.zkoss.poi.xssf.eventusermodel.XSSFReader;
import org.zkoss.poi.xssf.usermodel.XSSFWorkbook;
import org.zkoss.zss.api.Range;
import org.zkoss.zss.api.Ranges;
import org.zkoss.zss.api.model.CellData;

import javax.xml.parsers.*;
import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
 * <p>
 * Created by bill on 13/12/13.
 * <p>
 * Split and then refactored by EFC - does pre processing such as unzipping and extracting CSVs from sheets before being sent to the DB server.
 * <p>
 * Now uses Import Templates as a clearer method to define more complex importing required by Ed Broking, the idea of an Import Model with version sheets that can select from that model.
 * <p>
 * Lists of UploadedFile contain both info required to upload a file and the results of an upload.
 * <p>
 * Deals with reports, set definitions and data files. Report schedules and users are now dealt with in dedicated classes.
 * <p>
 * Uploading of reports is going to stay in here for the mo as they will likely be packaged in zip files along with data.
 */

public final class ImportService {

    public static final String dbPath = "/databases/";
    public static final String onlineReportsDir = "/onlinereports/";
    public static final String importTemplatesDir = "/importtemplates/";
    public static String LOCALIP = "127.0.0.1";
    private static final String IMPORTTEMPLATE = "importtemplate";
    public static final String IMPORTVERSION = "importversion";


    /* external entry point, moves the file to a temp directory in case pre processing is required
    (decompress or sheets in a book to individual csv files before sending to the db server).
     After that a little housekeeping.
     Added support for validation importing against a temporary copied database, a flag on
     the last field is a little hacky - down to the file (not sheet!) check for parameters that have been set in a big chunk on the pending uploads screen
     */
    public static List<UploadedFile> importTheFile(final LoggedInUser loggedInUser, final UploadedFile uploadedFile, Map<String, Map<String, String>> parametersPerFile) throws Exception { // setup just to flag it
        InputStream uploadFile = new FileInputStream(uploadedFile.getPath());
        if (loggedInUser.getDatabase() == null) {
            throw new Exception("No database set");
        }
        String fileName = uploadedFile.getFileName();
        String originalFilePath = uploadedFile.getPath();
        // perhaps could make slightly cleaner API calls
        File tempFile = File.createTempFile(fileName.substring(0, fileName.length() - 4) + "_", fileName.substring(fileName.length() - 4));
        tempFile.deleteOnExit();
        FileOutputStream fos = new FileOutputStream(tempFile);
        org.apache.commons.io.IOUtils.copy(uploadFile, fos);
        fos.close();

        uploadFile.close(); // windows requires this (though windows ideally should not be used in production), perhaps not a bad idea anyway
        uploadedFile.setPath(tempFile.getPath()); // I'm now allowing adjustment of paths like this - having the object immutable became impractical
        List<UploadedFile> processedUploadedFiles = checkForCompressionAndImport(loggedInUser, uploadedFile, new HashMap<>(), parametersPerFile, new AtomicInteger(0));
        if (!uploadedFile.isValidationTest()) {
            // persist on the database server
            SpreadsheetService.databasePersist(loggedInUser);
            // add to the uploaded list on the Manage Databases page
            // now jamming the import feedback in the comments

            UploadRecord uploadRecord = new UploadRecord(0, LocalDateTime.now(), loggedInUser.getUser().getBusinessId()
                    , loggedInUser.getDatabase().getId(), loggedInUser.getUser().getId()
                    , uploadedFile.getFileName() + (processedUploadedFiles.size() == 1 && processedUploadedFiles.get(0).getReportName() != null ? " - (" + processedUploadedFiles.get(0).getReportName() + ")" : ""), "", ManageDatabasesController.formatUploadedFiles(processedUploadedFiles, -1,true), originalFilePath);
            UploadRecordDAO.store(uploadRecord);
            // and update the counts on the manage database page
            AdminService.updateNameAndValueCounts(loggedInUser, loggedInUser.getDatabase());
        } else {
            // important todo - SOMEHOW CHECK IF THE SOURCE DB WAS UPDATED IN THE MEAN TIME AND IF SO RUN THE VALIDATION AGAIN
            System.out.println("Zapping temporary copy");
            RMIClient.getServerInterface(loggedInUser.getDatabaseServer().getIp()).zapTemporaryCopy(loggedInUser.getDataAccessToken());
        }
        return processedUploadedFiles;
    }

    public static String DONTLOAD = "DONTLOAD";

    // deals with unzipping if required - recursive in case there's a zip in a zip
    private static List<UploadedFile> checkForCompressionAndImport(final LoggedInUser loggedInUser, final UploadedFile uploadedFile, HashMap<String, ImportTemplateData> templateCache, Map<String, Map<String, String>> parametersPerFile, AtomicInteger count) throws Exception {
        List<UploadedFile> processedUploadedFiles = new ArrayList<>();
        if (uploadedFile.getFileName().endsWith(".zip") || uploadedFile.getFileName().endsWith(".7z")) {
            ZipUtil.explode(new File(uploadedFile.getPath()));
            // after exploding the original file is replaced with a directory
            File zipDir = new File(uploadedFile.getPath());
            List<File> files = new ArrayList<>(FileUtils.listFiles(zipDir, null, true));
            // should be sorting by xls first then size ascending
            files.sort((f1, f2) -> {
                //note - this does not sort zip files.... should they be first?
                if ((f1.getName().toLowerCase().endsWith(".xls") || f1.getName().toLowerCase().endsWith(".xlsx"))
                        && (!f2.getName().toLowerCase().endsWith(".xls") && !f2.getName().toLowerCase().endsWith(".xlsx"))) { // one is xls, the other is not
                    return -1;
                }
                if ((f2.getName().toLowerCase().endsWith(".xls") || f2.getName().toLowerCase().endsWith(".xlsx"))
                        && (!f1.getName().toLowerCase().endsWith(".xls") && !f1.getName().toLowerCase().endsWith(".xlsx"))) { // other way round
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
                // bit hacky to stop the loading but otherwise there'd just be another map
                boolean dontLoad = false;
                if (parametersPerFile != null && parametersPerFile.get(f.getName()) != null) {
                    if (DONTLOAD.equalsIgnoreCase(parametersPerFile.get(f.getName()).get(DONTLOAD))) {
                        dontLoad = true;
                    }
                    // don't change this to entries - the keys are converted to lower case
                    for (String key : parametersPerFile.get(f.getName()).keySet()) {
                        fileNameParams.put(key.toLowerCase(), parametersPerFile.get(f.getName()).get(key));
                    }
                }
                if (!dontLoad) {
                    UploadedFile zipEntryUploadFile = new UploadedFile(f.getPath(), names, fileNameParams, false, uploadedFile.isValidationTest());
                    processedUploadedFiles.addAll(checkForCompressionAndImport(loggedInUser, zipEntryUploadFile, templateCache, parametersPerFile, count));
                }
            }
        } else { // nothing to decompress
            // simple checks in case the wrong type of file is being uploaded here - will probably be removed later
            if (uploadedFile.getFileName().startsWith(CreateExcelForDownloadController.USERSFILENAME) && (loggedInUser.getUser().isAdministrator() || loggedInUser.getUser().isMaster())) { // then it's not a normal import, users/permissions upload. There may be more conditions here if so might need to factor off somewhere
                uploadedFile.setError("Please upload the users file in the users tab.");
                processedUploadedFiles.add(uploadedFile);
            } else if (uploadedFile.getFileName().equals(CreateExcelForDownloadController.REPORTSCHEDULESFILENAME) && (loggedInUser.getUser().isAdministrator() || loggedInUser.getUser().isMaster())) {
                uploadedFile.setError("Please upload the report schedules file in the schedules tab.");
                processedUploadedFiles.add(uploadedFile);
            } else if (uploadedFile.getFileName().toLowerCase().endsWith(".xls") || uploadedFile.getFileName().toLowerCase().endsWith(".xlsx")) {
                processedUploadedFiles.addAll(readBook(loggedInUser, uploadedFile, templateCache,count));
            } else {
                processedUploadedFiles.add(readPreparedFile(loggedInUser, uploadedFile, false, templateCache, count));
            }
        }
        return processedUploadedFiles;
    }

    //302200K
    // a book will be a report to upload or a workbook which has to be converted into a csv for each sheet
    private static List<UploadedFile> readBook(LoggedInUser loggedInUser, UploadedFile uploadedFile, HashMap<String, ImportTemplateData> templateCache, AtomicInteger count) throws Exception {
        long time = System.currentTimeMillis();
        Workbook book;
        OPCPackage opcPackage = null;
        // we now use apache POI which is faster than ZK but it has different implementations for .xls and .xlsx files
        try {
            FileInputStream fs = new FileInputStream(new File(uploadedFile.getPath()));
            if (uploadedFile.getFileName().toLowerCase().endsWith("xlsx")) {
                long quicktest = System.currentTimeMillis();
                opcPackage = OPCPackage.open(fs);
                book = new XSSFWorkbook(opcPackage);
                System.out.println("book open time " + (System.currentTimeMillis() - quicktest));
            } else {
                book = new HSSFWorkbook(fs);
            }
        } catch (Exception e) {
            e.printStackTrace();
            uploadedFile.setError(e.getMessage());
            return Collections.singletonList(uploadedFile);
        }
        if (!uploadedFile.isValidationTest()) {
            for (int sheetNo = 0; sheetNo < book.getNumberOfSheets(); sheetNo++) {
                Sheet sheet = book.getSheetAt(sheetNo);
                if (sheet.getSheetName().equalsIgnoreCase("Import Model")) {
                    if ((loggedInUser.getUser().isAdministrator() || loggedInUser.getUser().isDeveloper())) {
                        return Collections.singletonList(uploadImportTemplate(uploadedFile, loggedInUser));
                    }
                }
            }
            // is it the type of import template as required by Ben Jones
            Name importName = BookUtils.getName(book, ReportRenderer.AZIMPORTNAME);
            if (importName != null) {
                if ((loggedInUser.getUser().isAdministrator() || loggedInUser.getUser().isDeveloper())) {
                    return Collections.singletonList(uploadImportTemplate(uploadedFile, loggedInUser));
                }
            }


            String reportName = null;
            // a misleading name now we have the ImportTemplate object
            Name reportRange = BookUtils.getName(book, ReportRenderer.AZREPORTNAME);
            if (reportRange != null) {
                CellReference sheetNameCell = BookUtils.getNameCell(reportRange);
                reportName = book.getSheet(reportRange.getSheetName()).getRow(sheetNameCell.getRow()).getCell(sheetNameCell.getCol()).getStringCellValue();
            }
            if (reportName != null) {
                if ((loggedInUser.getUser().isAdministrator() || loggedInUser.getUser().isDeveloper())) {
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
                    } else {
                        or = new OnlineReport(0, LocalDateTime.now(), businessId, loggedInUser.getUser().getId(), loggedInUser.getDatabase().getName(), reportName, uploadedFile.getFileName(), "");
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
                }
            }
        }

        if (loggedInUser.getDatabase() == null) {
            uploadedFile.setError("no database set");
            return Collections.singletonList(uploadedFile);
        }
        Map<String, String> knownValues = new HashMap<>();
        // add a share of the initial excel load time to each of the sheet convert and processing times
        long sheetExcelLoadTimeShare = (System.currentTimeMillis() - time) / book.getNumberOfSheets();
        // with more than one sheet to convert this is why the function returns a list
        List<UploadedFile> toReturn = new ArrayList<>();
        for (int sheetNo = 0; sheetNo < book.getNumberOfSheets(); sheetNo++) {
            Sheet sheet = book.getSheetAt(sheetNo);
            List<UploadedFile> uploadedFiles = readSheet(loggedInUser, uploadedFile, sheet, knownValues, templateCache, count);
            for (UploadedFile uploadedFile1 : uploadedFiles) {
                uploadedFile1.addToProcessingDuration(sheetExcelLoadTimeShare / uploadedFiles.size());
            }
            toReturn.addAll(uploadedFiles);
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

    Known names not relevant on this data bit, now there's a split to the template source for the headings we just have it there
     */

    private static void rangeToCSV(Sheet sheet, AreaReference areaReference, CsvWriter csvW) throws Exception {
        int startRow = areaReference.getFirstCell().getRow();
        int endRow = areaReference.getLastCell().getRow();
        int startCol = areaReference.getFirstCell().getCol();
        int endCol = areaReference.getLastCell().getCol();
        for (int rNo = startRow; rNo <= endRow; rNo++) {
            for (int cNo = startCol; cNo <= endCol; cNo++) {
                String val = "";
                if (sheet.getRow(rNo) != null) {
                    val = getCellValue(sheet.getRow(rNo).getCell(cNo));
                }
                csvW.write(val.replace("\n", "\\\\n").replace("\t", "\\\\t"));//nullify the tabs and carriage returns.  Note that the double slash is deliberate so as not to confuse inserted \\n with existing \n
            }
            csvW.endRecord();
        }
    }

    private static void rangeToCSV(List<List<String>> templateSource, AreaReference areaReference, Map<String, String> knownNames, CsvWriter csvW) throws Exception {
        int startRow = areaReference.getFirstCell().getRow();
        int endRow = areaReference.getLastCell().getRow();
        int startCol = areaReference.getFirstCell().getCol();
        int endCol = areaReference.getLastCell().getCol();
        for (int rNo = startRow; rNo <= endRow; rNo++) {
            for (int cNo = startCol; cNo <= endCol; cNo++) {
                String val = "";
                if (templateSource.size() > rNo) {
                    if (templateSource.get(rNo).size() > cNo){
                        val = templateSource.get(rNo).get(cNo);
                    }
                }
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


    private static List<UploadedFile> readSheet(LoggedInUser loggedInUser, UploadedFile uploadedFile, Sheet sheet, Map<String, String> knownValues, HashMap<String, ImportTemplateData> templateCache, AtomicInteger count) {
        String sheetName = sheet.getSheetName();
        long time = System.currentTimeMillis();
        try {
            boolean importByNamedRegion = false;
            // reimplementing the old style import templates as used by Ben Jones
            // this could perhaps be tidied, is a mix of API calls as the data is a workbook but the template is the new style,
            // variable names could be better what with ,ap entries being used. TODO clean
            ImportTemplateData importTemplateData = getImportTemplateForUploadedFile(loggedInUser, uploadedFile, templateCache);
            if (importTemplateData != null) {
                for (String templateSheetName : importTemplateData.getSheets().keySet()) {
                    AreaReference importName = importTemplateData.getName(ReportRenderer.AZIMPORTNAME, templateSheetName);
                    if (importName != null) {
                        importByNamedRegion = true;
                        int row = importName.getFirstCell().getRow();
                        int col = importName.getFirstCell().getCol();
                        String valueToLookFor = null;
                        List<List<String>> sheetData = importTemplateData.getSheets().get(templateSheetName);
                        if (sheetData.size() > row && sheetData.get(row).size() > col){
                            valueToLookFor = sheetData.get(row).get(col);
                        }
                        Cell cell = null;
                        if (sheet.getRow(row) != null) {
                            cell = sheet.getRow(row).getCell(col);
                        }
                        if (cell != null && getCellValue(cell).equals(valueToLookFor)) {// then we have a match
                            //FIRST  glean information from range names
                            Map<String, AreaReference> namesForTemplate = importTemplateData.getNamesForSheet(templateSheetName);
                            for (Map.Entry<String, AreaReference> entry : namesForTemplate.entrySet()) {
                                AreaReference ref = entry.getValue();
                                if (ref.isSingleCell()) {
                                    int rowNo = ref.getFirstCell().getRow();
                                    int colNo = ref.getFirstCell().getCol();
                                    knownValues.put(entry.getKey(), getCellValue(sheet.getRow(rowNo).getCell(colNo)));
                                }
                            }
                            //now copy across the column headings in full
                            ArrayList<UploadedFile> toReturn = new ArrayList<>();
                            for (Map.Entry<String, AreaReference> entry : namesForTemplate.entrySet()) {
                                if (entry.getKey().toLowerCase().startsWith(ReportRenderer.AZCOLUMNHEADINGS)) {
                                    AreaReference dataRegion = importTemplateData.getName(ReportRenderer.AZDATAREGION + entry.getKey().toLowerCase().substring(ReportRenderer.AZCOLUMNHEADINGS.length()));
                                    if (dataRegion != null) {
                                        time = System.currentTimeMillis();
                                        File newTempFile = File.createTempFile("from" + uploadedFile.getPath() + sheetName, ".csv");
                                        newTempFile.deleteOnExit();
                                        // Think it's ok with a blank file created
                                        CsvWriter csvW = new CsvWriter(newTempFile.toString(), '\t', Charset.forName("UTF-8"));
                                        csvW.setUseTextQualifier(false);
                                        // this is called twice, for the column headers and then the data itself
                                        rangeToCSV(importTemplateData.getSheets().get(sheetName), entry.getValue(), knownValues, csvW);
                                        rangeToCSV(sheet, dataRegion, csvW);
                                        csvW.close();
                                        long convertTime = System.currentTimeMillis() - time;
//                                fos.close();
                                        List<String> names = new ArrayList<>(uploadedFile.getFileNames());
                                        names.add(entry.getKey().substring(ReportRenderer.AZCOLUMNHEADINGS.length()));
                                        // reassigning uploaded file so the correct object will be passed back on exception
                                        uploadedFile = new UploadedFile(newTempFile.getPath(), names, uploadedFile.getParameters(), true, uploadedFile.isValidationTest());
                                        uploadedFile.addToProcessingDuration(convertTime);
                                        toReturn.add(readPreparedFile(loggedInUser, uploadedFile, true, templateCache,count));
                                    }
                                }
                            }
                            return toReturn;
                        }
                    }
                }
            }
            if (importByNamedRegion) { // don't continue and try with the new method - if it was an old style import template and we got this far then it failed
                return new ArrayList<>();
            }
            // end check on Ben Jones style import
            long test = System.currentTimeMillis();
            File temp = File.createTempFile(uploadedFile.getPath() + sheetName, ".csv");
            String tempPath = temp.getPath();
            temp.deleteOnExit();
            FileOutputStream fos = new FileOutputStream(tempPath);
            CsvWriter csvW = new CsvWriter(fos, '\t', Charset.forName("UTF-8"));
            csvW.setUseTextQualifier(false);
            // poi convert - notably the iterators skip blank rows and cells hence the checking that indexes match
            int rowIndex = -1;
            boolean emptySheet = true;
            for (Row row : sheet) {
                emptySheet = false;
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
            System.out.println("current sheet to csv on " + sheetName + " " + (System.currentTimeMillis() - test));
            long convertTime = System.currentTimeMillis() - time;
            List<String> names = new ArrayList<>(uploadedFile.getFileNames());
            names.add(sheetName);
            // the sheet name might have parameters - try to get them
            Map<String, String> fileNameParams = new HashMap<>(uploadedFile.getParameters());
            addFileNameParametersToMap(sheetName, fileNameParams);
            // reassigning uploaded file so the correct object will be passed back on exception
            uploadedFile = new UploadedFile(tempPath, names, fileNameParams, true, uploadedFile.isValidationTest()); // true, it IS converted from a worksheet
            if (emptySheet) {
                uploadedFile.setError("Empty sheet : " + sheetName);
                return Collections.singletonList(uploadedFile);
            } else {
                UploadedFile toReturn = readPreparedFile(loggedInUser, uploadedFile, false, templateCache,count);
                // the UploadedFile will have the database server processing time, add the Excel stuff to it for better feedback to the user
                toReturn.addToProcessingDuration(convertTime);
                return Collections.singletonList(toReturn);
            }
        } catch (Exception e) {
            e.printStackTrace();
            Throwable t = e;
            while (t.getCause() != null) { // unwrap as much as possible
                t = t.getCause();
            }
            uploadedFile.setError(t.getMessage());
            return Collections.singletonList(uploadedFile);
        }
    }


    // things that can be read from "Parameters" in an import template sheet
    private static final String PRE_PROCESSOR = "pre-processor";
    private static final String PREPROCESSOR = "preprocessor";
    private static final String ADDITIONALDATAPROCESSOR = "additionaldataprocessor";
    private static final String POSTPROCESSOR = "postprocessor";
    private static final String NOFILEHEADINGS = "nofileheadings";
    private static final String LANGUAGE = "language";
    private static final String SKIPLINES = "skiplines";


    // copy the file to the database server if it's on a different physical machine then tell the database server to process it
    private static UploadedFile readPreparedFile(final LoggedInUser loggedInUser, UploadedFile uploadedFile,  boolean importTemplateUsedAlready, HashMap<String, ImportTemplateData> templateCache, AtomicInteger count) throws
            Exception {
        String templateName = uploadedFile.getFileName().replace("\\", "/");
        int slashpos = templateName.lastIndexOf("/");
        if (slashpos < 0) {
            slashpos = 0;
        }
        int lastDot = templateName.lastIndexOf(".");
        if (lastDot < 0) lastDot = templateName.length();
        templateName = templateName.substring(slashpos, lastDot);
        int blankPos = templateName.indexOf(" ");
        if (blankPos > 0) {
            templateName = templateName.substring(0, blankPos);
        }

        if (!templateName.toLowerCase().startsWith("sets") && !importTemplateUsedAlready) {
            ImportTemplateData importTemplateData = getImportTemplateForUploadedFile(loggedInUser, uploadedFile, templateCache);
            if (importTemplateData != null) { // new logic - we must have an import version
                // ok let's check here for the old style of import template as used by Ben Jones
                Map<String, String> templateParameters = new HashMap<>(); // things like pre processor, file encoding etc
                List<List<String>> standardHeadings = new ArrayList<>();
                // scan first for the main model
                for (String sheetName : importTemplateData.getSheets().keySet()) {
                    if (sheetName.equalsIgnoreCase("Import Model") || sheetName.equalsIgnoreCase(templateName)) {
                        importSheetScan(importTemplateData.getSheets().get(sheetName), null, standardHeadings, null, templateParameters, null);
                        break;
                    }
                }
                // if there are no standard headings, then read the file without adjustment
                if (!standardHeadings.isEmpty()) {
                    Map<TypedPair<Integer, Integer>, String> topHeadings = new HashMap<>();
                    // specific headings on the file we're loading
                    List<List<String>> versionHeadings = new ArrayList<>();
                    // this *should* be single line, used to lookup information from the Import Model
                    List<List<String>> headingReference = new ArrayList<>();
                    // a "version" - similar to the import model parsing but the headings can be multi level (double decker) and not at the top thus allowing for top headings
                    for (String sheetName : importTemplateData.getSheets().keySet()) {
                        if (sheetName.equalsIgnoreCase(uploadedFile.getParameter(IMPORTVERSION))) {
                            // unlike the "default" mode there can be a named range for the headings here so
                            AreaReference headingsName = importTemplateData.getName("az_Headings" + sheetName);
                            if (headingsName != null) { // we have to have it or don't bother!
                                uploadedFile.setSkipLines(headingsName.getFirstCell().getRow());
                                // parameters and lookups are cumulative, pass through the same maps
                                importSheetScan(importTemplateData.getSheets().get(sheetName), topHeadings, headingReference, versionHeadings, templateParameters, headingsName);
                            } else {
                                throw new Exception("Import version sheet " + uploadedFile.getParameter(IMPORTVERSION) + " found but couldn't find az_Headings" + sheetName);
                            }
                        }
                    }

                    if (templateParameters.get(PRE_PROCESSOR) != null) {
                        uploadedFile.setPreProcessor(templateParameters.get(PRE_PROCESSOR));
                    }
                    if (templateParameters.get(PREPROCESSOR) != null) {
                        uploadedFile.setPreProcessor(templateParameters.get(PREPROCESSOR));
                    }
                    if (templateParameters.get(ADDITIONALDATAPROCESSOR) != null) {
                        uploadedFile.setAdditionalDataProcessor(templateParameters.get(ADDITIONALDATAPROCESSOR));
                    }
                    if (templateParameters.get(POSTPROCESSOR) != null) {
                        uploadedFile.setPostProcessor(templateParameters.get(POSTPROCESSOR));
                    }
                    if (templateParameters.get(LANGUAGE) != null) {
                        String languages = templateParameters.get(LANGUAGE);
                        String[] splitLanguages = languages.split(",");
                        List<String> languagesList = new ArrayList<>(Arrays.asList(splitLanguages));
                        languagesList.add(StringLiterals.DEFAULT_DISPLAY_NAME);
                        uploadedFile.setLanguages(languagesList);
                    }
                    if (versionHeadings.isEmpty()) { // ok we go vanilla, not a version in a template, just the Import Model sheet
                        if (templateParameters.get(SKIPLINES) != null && NumberUtils.isDigits(templateParameters.get(SKIPLINES))) {
                            uploadedFile.setSkipLines(Integer.parseInt(templateParameters.get(SKIPLINES)));
                        }
                        if (templateParameters.get(NOFILEHEADINGS) != null) { // this parameter allows simple headings to be built from multiple cells
                            List<String> simpleHeadings = new ArrayList<>();
                            for (List<String> headings : standardHeadings) {
                                StringBuilder azquoHeading = new StringBuilder();
                                for (int i = 0; i < headings.size(); i++) {
                                    if (i > 0) {
                                        azquoHeading.append(";");
                                    }
                                    azquoHeading.append(headings.get(i));
                                }
                                simpleHeadings.add(azquoHeading.toString());
                            }
                            uploadedFile.setSimpleHeadings(simpleHeadings);
                        } else {
                            // we assume the first line are file headings and anything below needs to be joined together into an Azquo heading
                            // we now support headingsNoFileHeadingsWithInterimLookup in a non - version context
                            List<TypedPair<String, String>> headingsNoFileHeadingsWithInterimLookup = new ArrayList<>();
                            Map<List<String>, TypedPair<String, String>> headingsByLookupWithInterimLookup = new HashMap<>();
                            for (List<String> headings : standardHeadings) {
                                if (!headings.isEmpty()) {
                                    String fileHeading = null;
                                    StringBuilder azquoHeading = new StringBuilder();
                                    for (int i = 0; i < headings.size(); i++) {
                                        if (i == 0) {
                                            fileHeading = headings.get(i);
                                        } else {
                                            if (i > 1) {
                                                azquoHeading.append(";");
                                            }
                                            azquoHeading.append(headings.get(i));
                                        }
                                    }
                                    // there is no second value to the typed pair - that's only used when there's a version. The field says "WithInterimLookup" but there is no interim lookup where there's one sheet
                                    if (fileHeading.isEmpty()) {
                                        headingsNoFileHeadingsWithInterimLookup.add(new TypedPair<>(azquoHeading.toString(), null));
                                    } else {
                                        headingsByLookupWithInterimLookup.put(Collections.singletonList(fileHeading), new TypedPair<>(azquoHeading.toString(), null));
                                    }
                                }
                            }
                            uploadedFile.setHeadingsByFileHeadingsWithInterimLookup(headingsByLookupWithInterimLookup);
                            uploadedFile.setHeadingsNoFileHeadingsWithInterimLookup(headingsNoFileHeadingsWithInterimLookup);
                        }
                    } else {

                        // the thing here is to add the version headings as file headings looking up the Azquo headings from the Import Model
                        Map<List<String>, TypedPair<String, String>> headingsByFileHeadingsWithInterimLookup = new HashMap<>();
                        // and the headings without reference to the
                        List<TypedPair<String, String>> headingsNoFileHeadingsWithInterimLookup = new ArrayList<>();
                        /* There may be references without headings but not the other way around (as in we'd just ignore the headings)
                         * hence references needs to be the outer loop adding version headings where it can find them
                         * */
                        int index = 0;
                        for (List<String> headingReferenceColumn : headingReference) {
                            if (headingReferenceColumn.size() > 0 && !headingReferenceColumn.get(0).isEmpty()) {// we fill gaps when parsing the standard headings so there may be blanks in here, ignore them!
                                String reference = headingReferenceColumn.get(0); // the reference is the top one
                                List<String> azquoClauses = new ArrayList<>(); // in here will go the Azquo clauses both looked up from the Import Model and any that might have been added here
                                // ok now we're going to look for this in the standard headings by the "top" heading
                                Iterator<List<String>> standardHeadingsIterator = standardHeadings.iterator();
                                while (standardHeadingsIterator.hasNext()) {
                                    List<String> standardHeadingsColumn = standardHeadingsIterator.next();
                                    if (!standardHeadingsColumn.isEmpty() && standardHeadingsColumn.get(0).equalsIgnoreCase(reference)) {
                                        standardHeadingsIterator.remove(); // later when checking for required we don't want to add any again
                                        azquoClauses.addAll(standardHeadingsColumn.subList(1, standardHeadingsColumn.size()));
                                    }
                                }
                                if (azquoClauses.isEmpty()) {
                                    throw new Exception("On import version sheet " + uploadedFile.getParameter(ImportService.IMPORTVERSION) + " no headings on Import Model found for " + reference + " - was this referenced twice?");
                                } else if (headingReference.get(index).size() > 1) { // add the extra ones
                                    azquoClauses.addAll(headingReference.get(index).subList(1, headingReference.get(index).size()));
                                }
                                StringBuilder azquoHeadingsAsString = new StringBuilder();
                                for (String clause : azquoClauses) {
                                    if (azquoHeadingsAsString.length() > 0 && !clause.startsWith(".")) {
                                        azquoHeadingsAsString.append(";");
                                    }
                                    azquoHeadingsAsString.append(clause);
                                }
                                // finally get the file headings if applicable
                                if (versionHeadings.size() > index && !versionHeadings.get(index).isEmpty()) {
                                    headingsByFileHeadingsWithInterimLookup.put(versionHeadings.get(index), new TypedPair<>(azquoHeadingsAsString.toString(), reference));
                                } else {
                                    headingsNoFileHeadingsWithInterimLookup.add(new TypedPair<>(azquoHeadingsAsString.toString(), reference));
                                }
                            }
                            index++;
                        }
                        // and now required
                        for (List<String> standardHeadingsColumn : standardHeadings) {
                            boolean required = false;
                            for (String clause : standardHeadingsColumn) {
                                if (clause.equalsIgnoreCase("required")) {
                                    required = true;
                                    break;
                                }
                            }
                            // ok criteria added after (may need refactoring), if a top heading with a value (surrounded by ``) matches a heading on the import model use that also
                            if (required || (!standardHeadingsColumn.isEmpty() && topHeadings.values().contains("`" + standardHeadingsColumn.get(0) + "`"))) {
                                StringBuilder azquoHeadingsAsString = new StringBuilder();
                                // clauses after the first cell
                                for (String clause : standardHeadingsColumn.subList(1, standardHeadingsColumn.size())) {
                                    if (azquoHeadingsAsString.length() == 0) {
                                        azquoHeadingsAsString = new StringBuilder(clause);
                                    } else {
                                        if (!clause.startsWith(".")) {
                                            azquoHeadingsAsString.append(";");
                                        }
                                        azquoHeadingsAsString.append(clause);
                                    }
                                }
                                // no headings but it has the other bits - will be jammed on the end after file
                                headingsNoFileHeadingsWithInterimLookup.add(new TypedPair<>(azquoHeadingsAsString.toString(), standardHeadingsColumn.get(0)));
                            }
                        }
                        uploadedFile.setHeadingsByFileHeadingsWithInterimLookup(headingsByFileHeadingsWithInterimLookup);
                        uploadedFile.setHeadingsNoFileHeadingsWithInterimLookup(headingsNoFileHeadingsWithInterimLookup);
                        uploadedFile.setTopHeadings(topHeadings);
                    }
                }
            }
        }

        // new thing - pre processor on the report server
        if (uploadedFile.getPreProcessor() != null) {
            File file = new File(SpreadsheetService.getGroovyDir() + "/" + uploadedFile.getPreProcessor());
            if (file.exists()) {
                String oldImportVersion = uploadedFile.getParameter(IMPORTVERSION);
                System.out.println("Groovy pre processor running  . . . ");
                Object[] groovyParams = new Object[1];
                groovyParams[0] = uploadedFile;
                GroovyShell shell = new GroovyShell();
                final Script script = shell.parse(file);
                System.out.println("loaded groovy " + file.getPath());
                // overly wordy way to override the path
                try {
                    uploadedFile.setPath((String) script.invokeMethod("fileProcess", groovyParams));
                } catch (Exception e) {
                    uploadedFile.setError("Preprocessor error in " + uploadedFile.getPreProcessor() + " : " + e.getMessage());
                    count.incrementAndGet();
                    return uploadedFile;
                }
                // todo - the import version switching will be broken!
                if (oldImportVersion != null && !oldImportVersion.equalsIgnoreCase(uploadedFile.getParameter(IMPORTVERSION))) { // the template changed! Call this function again to load the new template
                    // there is a danger of a circular reference - protect against that?
                    // must clear template based parameters, new object
                    UploadedFile fileToProcessAgain = new UploadedFile(uploadedFile.getPath(), uploadedFile.getFileNames(), uploadedFile.getParameters(), true, uploadedFile.isValidationTest());
                    return readPreparedFile(loggedInUser, fileToProcessAgain,false, templateCache, count);
                }
            } else {
                uploadedFile.setError("unable to find preprocessor : " + uploadedFile.getPreProcessor());
                count.incrementAndGet();
                return uploadedFile;
            }
        }

        DatabaseServer databaseServer = loggedInUser.getDatabaseServer();
        DatabaseAccessToken databaseAccessToken = loggedInUser.getDataAccessToken();
        // right - here we're going to have to move the file if the DB server is not local.
        if (!databaseServer.getIp().equals(LOCALIP)) {// the call via RMI is the same the question is whether the path refers to this machine or another
            uploadedFile.setPath(SFTPUtilities.copyFileToDatabaseServer(new FileInputStream(uploadedFile.getPath()), databaseServer.getSftpUrl()));
        }
        // used to be processed file, I see no advantage to that
        uploadedFile = RMIClient.getServerInterface(databaseServer.getIp()).readPreparedFile(databaseAccessToken
                , uploadedFile
                , loggedInUser.getUser().getName());
        // run any executes defined in the file
        List<List<List<String>>> systemData2DArrays = new ArrayList<>();
        if (uploadedFile.getPostProcessor() != null) {
            // set user choices to file params, could be useful to the execute
            for (String choice : uploadedFile.getParameters().keySet()) {
                SpreadsheetService.setUserChoice(loggedInUser.getUser().getId(), choice, uploadedFile.getParameter(choice));
            }
            loggedInUser.copyMode = uploadedFile.isValidationTest();
            uploadedFile.setPostProcessingResult(ReportExecutor.runExecute(loggedInUser, uploadedFile.getPostProcessor(), systemData2DArrays, uploadedFile.getProvenanceId()).toString());
        }
        loggedInUser.copyMode = false;
        if (!systemData2DArrays.isEmpty()) {
            // then we had some validation results, need to identify lines in the file with problems
            // ok leaving this for the mo. I have all the data I need it's just a question of the structure to store it, as we have rejected lines in Uploaded file so we'll have warning lines and possibly
            // error descriptions. Leaving the warning lines as a map as they may not be added in order, will sort the lines and repackage after
            // todo - typedpair is being used too much - it's causing obscurity. Roll more small classes . . .
            Map<Integer, UploadedFile.WarningLine> warningLineMap = new HashMap<>();
            //System.out.println("system 2d arrays : " + systemData2DArrays);
            for (List<List<String>> systemData2DArray : systemData2DArrays) {
                if (systemData2DArray.size() > 1) { // no point doing anything if it's not!
                    Map<String, Map<String, String>> errorLines = new HashMap<>();
                    String keyColumn = systemData2DArray.get(0).get(0); // that will define what we want to lookup in the file e.g. policy number
                    ArrayList<String> errorHeadings = new ArrayList<>();
                    if (!keyColumn.isEmpty()) {
                        // ok there might be multiple columns of errors - the heading will include the short code in [] and the error may be specific to the line
                        List<String> headings = systemData2DArray.get(0);
                        for (int i = 1; i < headings.size(); i++) { // rest of the headings after the keyColumn
                            if (headings.get(i).isEmpty()) {
                                break;
                            }
                            errorHeadings.add(headings.get(i));
                        }
                        for (int i = 1; i < systemData2DArray.size(); i++) {// start from the second row
                            List<String> row = systemData2DArray.get(i);
                            if (!row.get(0).isEmpty()) { // if there's nothing in the key column we can't look up the line
                                int colIndex = 1;
                                for (String errorHeading : errorHeadings) {
                                    if (!row.get(colIndex).isEmpty()) {
                                        errorLines.computeIfAbsent(row.get(0).toLowerCase(), t -> new HashMap<>()).put(errorHeading, row.get(colIndex));
                                    }
                                    colIndex++;
                                }
                            }
                        }
                    }
                    if (!errorLines.isEmpty()) { // then I need to find them. First thing to do is find the relevant column
                        // going to try based on feedback in the processed file
                        if (uploadedFile.getFileHeadings() != null) { // if we don't have the original set of headings we won't be able to check the file
                            int index = 0;
                            for (List<String> fileHeading : uploadedFile.getFileHeadings()) {
//                            for (String subHeading : fileHeading) { // currently looking up against the model reference not the file reference
                                TypedPair<String, String> stringStringTypedPair = uploadedFile.getHeadingsByFileHeadingsWithInterimLookup().get(fileHeading);
                                // so the current criteria - it's a heading which looked up something on the model and it's the lookup which is the name we're interested in. Import Model having the canonical name for there purposes
                                // that lookup name is the second of the typed pair
                                if (stringStringTypedPair != null && stringStringTypedPair.getSecond() != null && stringStringTypedPair.getSecond().equalsIgnoreCase(keyColumn.trim())) {
                                    // we have a winner
                                    break;
                                }
                                index++;
                            }
                            if (index < uploadedFile.getFileHeadings().size()) { // then we found the relevant column
                                // I'm going to go to the server code to look this up
                                Map<Integer, TypedPair<String, String>> linesWithValuesInColumn = RMIClient.getServerInterface(databaseServer.getIp()).getLinesWithValuesInColumn(uploadedFile, index, new HashSet<>(errorLines.keySet()));// new hash set to be serializable . . .
                                // ok so we should now finally have the information we need across the two maps (linesWithValuesInColumn and errorLines)
                                // What needs to go into a table is the line number, the line itself and error(s) for that line, there could be multiple errors for the line
                                for (Integer key : linesWithValuesInColumn.keySet()) {
                                    warningLineMap.computeIfAbsent(key, t -> new UploadedFile.WarningLine(key, linesWithValuesInColumn.get(key).getSecond())).addErrors(errorLines.get(linesWithValuesInColumn.get(key).getFirst().toLowerCase()));
                                }
                            }// else error?
                        }
                    }

                }
            }
            if (!warningLineMap.isEmpty()) {
                ArrayList<Integer> sort = new ArrayList<>(warningLineMap.keySet());
                Collections.sort(sort);
                for (Integer lineNo : sort) {
                    uploadedFile.addToWarningLines(warningLineMap.get(lineNo));
                }
            }
        }
        count.incrementAndGet();
        return uploadedFile;
    }

    // in this context the custom headings range is for when there are multiple versions of an importer based off a master sheet -
    // e.g. the big Ed Broking import sheet for Risk which will have sub sheets - these sheets will have a named range,

    enum ImportSheetScanMode {OFF, TOPHEADINGS, CUSTOMHEADINGS, STANDARDHEADINGS, PARAMETERS}

    private static void importSheetScan(List<List<String>> sheet
            , Map<TypedPair<Integer, Integer>, String> topHeadings
            , List<List<String>> standardHeadings
            , List<List<String>> customHeadings
            , Map<String, String> templateParameters
            , AreaReference customHeadingsRange) {
        int rowIndex = -1;
        // if a range is passed this means scan for top headings before, then the customHeadingsRange range is specific file headings,
        // *then* headings which are a lookup to the model and any additional clauses, then params and lookups as usual
        // otherwise standard headings by default
        ImportSheetScanMode mode = customHeadingsRange != null ? ImportSheetScanMode.TOPHEADINGS : ImportSheetScanMode.STANDARDHEADINGS;
        for (List<String> row : sheet) {
            rowIndex++;
            if (mode == ImportSheetScanMode.TOPHEADINGS) {// top headings will be followed by custom headings
                if (rowIndex >= customHeadingsRange.getFirstCell().getRow()) { // there are custom headings and we've hit them
                    mode = ImportSheetScanMode.CUSTOMHEADINGS;
                }
            }
            if (mode == ImportSheetScanMode.CUSTOMHEADINGS) {
                if (rowIndex > customHeadingsRange.getLastCell().getRow()) { // end of custom headings, switch to standard
                    mode = ImportSheetScanMode.STANDARDHEADINGS;
                }
            }
            int cellIndex = -1;
            // unfortuenately while it seems POI might skip blank lines it seems it might also have blank cell values (this may not be a fault of POI, perhaps an Excel quirk)
            // regardless I need to check for a line only having blank cells and adjusting modes accordingly
            boolean blankLine = true;


            String firstCellValue = null;
            for (String cellValue : row) {
                cellIndex++;
                if (!cellValue.isEmpty()) {
                    blankLine = false;
                    if (mode == ImportSheetScanMode.TOPHEADINGS) {
                        // pretty simple really - jam any values found in the map!
                        if (!topHeadings.values().contains(cellValue)) { // not allowing multiple identical top headings. Typically can happen from a merge spreading across cells
                            topHeadings.put(new TypedPair<>(rowIndex, cellIndex), cellValue);
                        }
                    } else if (mode == ImportSheetScanMode.CUSTOMHEADINGS) { // custom headings to be used for lookup on files - I'll watch for limiting by column though it hasn't been used yet
                        if (customHeadingsRange.getFirstCell().getCol() == -1 || (cellIndex >= customHeadingsRange.getFirstCell().getCol() && cellIndex <= customHeadingsRange.getLastCell().getCol())) {
                            while (customHeadings.size() <= (cellIndex + 1)) { // make sure there are enough lists to represent the heading columns were adding to
                                customHeadings.add(new ArrayList<>());
                            }
                            customHeadings.get(cellIndex).add(cellValue);
                        }
                    } else if (mode == ImportSheetScanMode.STANDARDHEADINGS) { // build headings
                        // make sure there are enough lists to represent the heading columns we're adding to. Uses "while" as blank columns could make the cellIndex increase more than one
                        while (standardHeadings.size() <= cellIndex) {
                            List<String> newColumn = new ArrayList<>();
                            /* we're supporting extra "not in the file" headings as in composition headings in here, this means that if the previous column has more than one entry
                             as in we're NOT on the first line of the headings then add a space at the top which will indicate azquo headings but no file headings.
                             Essentially make up the whitespace that (understandably) might not be there in the POI model*/
                            standardHeadings.add(newColumn);
                            // check the previous column size if it exists and add spaces as required
                            if (standardHeadings.size() > 1 && standardHeadings.get(standardHeadings.size() - 2).size() > 1) { // -2 as we just added a column!
                                for (int i = 1; i < standardHeadings.get(standardHeadings.size() - 2).size(); i++) { // start at one, if the size is two for example we only add one blank
                                    newColumn.add("");
                                }
                            }
                            // this means there will be another go on the loop, fill in the space on the bottom cell of the column as the add below won't happen to this column, it must have been blank
                            if (standardHeadings.size() < cellIndex) {
                                newColumn.add("");
                            }
                        }
                        standardHeadings.get(cellIndex).add(cellValue);
                                /* outside of headings mode we grab the first cell regardless to check for mode switches,
                                it will also be used as the key in key/pair parsing for lookups and import template parameters
                                 */
                    } else if (firstCellValue == null) {
                        firstCellValue = cellValue;
                        if ("PARAMETERS".equalsIgnoreCase(firstCellValue)) { // string literal move?
                            mode = ImportSheetScanMode.PARAMETERS;
                        }
                    } else { // after the first cell when not headings
                        // yes extra cells will ovrride subsequent key pair values. Since this would be an incorrect sheet I'm not currently bothered by this
                        if (mode == ImportSheetScanMode.PARAMETERS) { // gathering parameters
                            templateParameters.put(firstCellValue.toLowerCase(), cellValue);
                        }
                    }
                }
            }
            if (blankLine && mode != ImportSheetScanMode.TOPHEADINGS) { // top headings will tolerate blank lines, other modes won't so switch off
                mode = ImportSheetScanMode.OFF;
            }
        }
    }

    /* for parsing parameters out of a file name. Cumulative if files in zips or sheets in workbooks
     have parameters as well as their "parent" file */
    public static void addFileNameParametersToMap(String fileName, Map<String, String> map) {
        try {
            int bracketPos = 0;
            while (bracketPos >= 0) {
                bracketPos = fileName.indexOf("(", bracketPos + 1);
                if (bracketPos > 0) {
                    int endBrackets = fileName.indexOf(")", bracketPos);
                    if (endBrackets > 0) {
                        String parseString = fileName.substring(bracketPos + 1, endBrackets);
                        if (parseString.contains("=")) {
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
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("Can't parse file name parameters correctly");
            e.printStackTrace();
        }
    }

    public static SimpleDateFormat YYYYMMDD = new SimpleDateFormat("yyyy-MM-dd");

    private static DataFormatter df = new DataFormatter();

    // POI Version, used when converting sheets to csv. Essentially get a value of the cell as either an unformatted number or as a string similar to how it
    // is rendered in Excel, Some hacking to standardise date formats and remove escape characters

    public static String getCellValue(Cell cell) {
        String returnString = "";
        if (cell == null) {
            return "";
        }
        //if (colCount++ > 0) bw.write('\t');
        if (cell.getCellType() == Cell.CELL_TYPE_STRING || (cell.getCellType() == Cell.CELL_TYPE_FORMULA && cell.getCachedFormulaResultType() == Cell.CELL_TYPE_STRING)) {
            try {
                returnString = cell.getStringCellValue();// I assume means formatted text?
            } catch (Exception ignored) {
            }
        } else if (cell.getCellType() == Cell.CELL_TYPE_NUMERIC || (cell.getCellType() == Cell.CELL_TYPE_FORMULA && cell.getCachedFormulaResultType() == Cell.CELL_TYPE_NUMERIC)) {
            // first we try to get it without locale - better match on built in formats it seems
            String dataFormat = BuiltinFormats.getBuiltinFormat(cell.getCellStyle().getDataFormat());
            if (dataFormat == null) {
                dataFormat = cell.getCellStyle().getDataFormatString();
            }
            Double returnNumber = cell.getNumericCellValue();
            returnString = returnNumber.toString();
            if (returnString.contains("E")) {
                returnString = String.format("%f", returnNumber);
            }
            if (returnNumber % 1 == 0) {
                // specific condition - integer and format all 000, then actually use the format. For zip codes
                if (dataFormat.length() > 1 && dataFormat.contains("0") && dataFormat.replace("0", "").isEmpty()) {
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
                    if (dataFormat.length() > 6) {
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

        // Deal with merged cells, not sure if there's a performance issue here? If a heading spans successive cells need to have the span value
        if (returnString.isEmpty() && cell.getSheet().getNumMergedRegions() > 0) {
            int rowIndex = cell.getRowIndex();
            int cellIndex = cell.getColumnIndex();
            for (int i = 0; i < cell.getSheet().getNumMergedRegions(); i++) {
                CellRangeAddress region = cell.getSheet().getMergedRegion(i); //Region of merged cells
                //check first cell of the region
                if (rowIndex == region.getFirstRow() && // logic change - only do the merge thing on the first column
                        cellIndex > region.getFirstColumn() // greater than, we're only interested if not the first column
                        && cellIndex <= region.getLastColumn()
                        /*&& rowIndex >= region.getFirstRow()
                        && rowIndex <= region.getLastRow()*/
                ) {
                    returnString = getCellValue(cell.getSheet().getRow(region.getFirstRow()).getCell(region.getFirstColumn()));
                }
            }
        }
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

    // similar to uploading a report
    public static UploadedFile uploadImportTemplate(UploadedFile uploadedFile, LoggedInUser loggedInUser) throws
            IOException {
        long time = System.currentTimeMillis();
        uploadedFile.setDataModified(true); // ok so it's not technically data modified but the file has been processed correctly.
        int businessId = loggedInUser.getUser().getBusinessId();
        String pathName = loggedInUser.getBusinessDirectory();
        ImportTemplate importTemplate = ImportTemplateDAO.findForNameAndBusinessId(uploadedFile.getFileName(), loggedInUser.getUser().getBusinessId());
        if (importTemplate != null) {
            // zap the old one first
            try {
                Files.deleteIfExists(Paths.get(SpreadsheetService.getHomeDir() + dbPath + pathName + importTemplatesDir + importTemplate.getFilenameForDisk()));
            } catch (Exception e) {
                System.out.println("problem deleting old template");
                e.printStackTrace();
            }
            importTemplate.setFilename(uploadedFile.getFileName()); // it might have changed, I don't think much else under these circumstances
            importTemplate.setDateCreated(LocalDateTime.now());
        } else {
            importTemplate = new ImportTemplate(0, LocalDateTime.now(), businessId, loggedInUser.getUser().getId(), uploadedFile.getFileName(), uploadedFile.getFileName(), ""); // default to ZK now
        }
        ImportTemplateDAO.store(importTemplate);
        Path fullPath = Paths.get(SpreadsheetService.getHomeDir() + dbPath + pathName + importTemplatesDir + importTemplate.getFilenameForDisk());
        Files.createDirectories(fullPath.getParent()); // in case it doesn't exist
        Files.copy(Paths.get(uploadedFile.getPath()), fullPath); // and copy
        // ---- end report uploading
        uploadedFile.setImportTemplate(true);
        uploadedFile.setProcessingDuration(System.currentTimeMillis() - time);
        return uploadedFile;
    }

    public static ImportTemplateData getImportTemplateForUploadedFile(LoggedInUser loggedInUser, UploadedFile uploadedFile, HashMap<String, ImportTemplateData> templateCache) throws Exception {
        String importTemplateName = uploadedFile != null ? uploadedFile.getParameter(IMPORTTEMPLATE) : null;
        // make a guess at the import template if it wasn't explicitly specified
        if (importTemplateName == null) {
            importTemplateName = loggedInUser.getDatabase().getName() + " import templates";
        }
        if (importTemplateName.endsWith(".xlsx")) importTemplateName = importTemplateName.replace(".xlsx", "");
        // todo - quick event based POI lookup to check for an import version and associated bits if the file has an import sheet
        String finalImportTemplateName = importTemplateName;
        if (templateCache != null) {
            return templateCache.computeIfAbsent(importTemplateName, t -> {
                        try {
                            return getImportTemplate(finalImportTemplateName, loggedInUser);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        return null;
                    }
            );
        } else return getImportTemplate(importTemplateName, loggedInUser);
    }

    // is returning a string the best idea?
    public static String testImportTemplateForTemplateAndVersion(LoggedInUser loggedInUser, String template, String version) throws Exception {
        // similar logic to above/below but we're looking for a very quick read of the file's contents
        if (template == null) {
            template = loggedInUser.getDatabase().getName() + " import templates";
        }
        if (template.endsWith(".xlsx")) template = template.replace(".xlsx", "");
        ImportTemplate importTemplate = ImportTemplateDAO.findForNameAndBusinessId(template, loggedInUser.getUser().getBusinessId());
        if (importTemplate != null) {
            Set<String> sheetNames = new HashSet<>();
            Set<String> nameNames = new HashSet<>();
            getSheetAndNamedRangesNamesQuicklyFromXLSX(SpreadsheetService.getHomeDir() + dbPath + loggedInUser.getBusinessDirectory() + importTemplatesDir + importTemplate.getFilenameForDisk(), sheetNames, nameNames);
            if (sheetNames.contains("import model")) { // then we need to check for the version!
                if (version == null || version.isEmpty()) {
                    return "Import version required";
                }
                if (sheetNames.contains(version.toLowerCase())) {
                    if (!nameNames.contains("az_Headings".toLowerCase() + version.toLowerCase())) {
                        return "az_Headings" + version + "not found for sheet";
                    }
                } else {
                    return "Import version not found in template";
                }
            }
        } else {
            return "Import Template not found";
        }
        return "ok";
    }


    private static ImportTemplateData getImportTemplate(String importTemplateName, LoggedInUser loggedInUser) throws Exception {
        ImportTemplate importTemplate = ImportTemplateDAO.findForNameAndBusinessId(importTemplateName, loggedInUser.getUser().getBusinessId());
        if (importTemplate != null) {

            // chunks of this will be factored off later when I want faster data file conversion
            ImportTemplateData importTemplateData = new ImportTemplateData();
            // I'm not supporting xls templates
            OPCPackage opcPackage = OPCPackage.open(new File(SpreadsheetService.getHomeDir() + dbPath + loggedInUser.getBusinessDirectory() + importTemplatesDir + importTemplate.getFilenameForDisk()));
            // we're going to event based reading, it will bypass errors that can jam poi
            XSSFReader xssfReader = new XSSFReader(opcPackage);
            ReadOnlySharedStringsTable strings = new ReadOnlySharedStringsTable(opcPackage);
            StylesTable styles = xssfReader.getStylesTable();

            // grab merges, hope this won't "cost" on big sheets
            /*
            can I do something like

            String strpath="/var/nagios.log";
ReversedLinesFileReader fr = new ReversedLinesFileReader(new File(strpath));
String ch;
int time=0;
String Conversion="";
do {
    ch = fr.readLine();
    out.print(ch+"<br/>");
} while (ch != null);
fr.close();

             */
            XSSFReader.SheetIterator iter = (XSSFReader.SheetIterator) xssfReader.getSheetsData();
            Map<String, List<String>> mergesMap = new HashMap<>();
            while (iter.hasNext()) {
                InputStream stream = iter.next();
                InputSource sheetSource = new InputSource(stream);
                SAXParserFactory saxFactory = SAXParserFactory.newInstance();
                ArrayList<String> merges = new ArrayList<>();
                try {
                    SAXParser saxParser = saxFactory.newSAXParser();
                    XMLReader sheetParser = saxParser.getXMLReader();
                    sheetParser.setContentHandler(new MergedCellsHandler(merges));
                    sheetParser.parse(sheetSource);
                } catch (ParserConfigurationException e) {
                    throw new RuntimeException("SAX parser appears to be broken - " + e.getMessage());
                }
                if (!merges.isEmpty()) {
                    mergesMap.put(iter.getSheetName(), merges);
                }
                stream.close();
            }

/*            iter = (XSSFReader.SheetIterator) xssfReader.getSheetsData();
            while (iter.hasNext()) {
                long test = System.currentTimeMillis();
                InputStream stream = iter.next();
                System.out.println("xml for " + iter.getSheetName());
                System.out.println(IOUtils.toString(stream));
                stream.close();
            }*/
            iter = (XSSFReader.SheetIterator) xssfReader.getSheetsData();
            while (iter.hasNext()) {
                StringListsEventDataRecipient stringListsEventDataRecipient = new StringListsEventDataRecipient();
                InputStream stream = iter.next();
                processSheet(styles, strings, stream, mergesMap.get(iter.getSheetName()), stringListsEventDataRecipient);
                stream.close();
                importTemplateData.getSheets().put(iter.getSheetName().trim(), stringListsEventDataRecipient.getData());
            }
            // ok now the names

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
//        System.out.println(IOUtils.toString(xssfReader.getWorkbookData()));
            Document workbookXML = builder.parse(xssfReader.getWorkbookData());
            workbookXML.getDocumentElement().normalize(); // probably fine on smaller XML, don't want to do on the big stuff
            NodeList nList = workbookXML.getElementsByTagName("definedName");
            for (int temp = 0; temp < nList.getLength(); temp++) {
                Node nNode = nList.item(temp);
                if (nNode.getNodeType() == Node.ELEMENT_NODE) {
                    Element eElement = (Element) nNode;
                    String nameName = eElement.getAttribute("name");
                    if (nameName != null) {
                        importTemplateData.putName(nameName, eElement.getTextContent());
                    }
                    //System.out.println("name : "  + eElement.getAttribute("name"));
                    //System.out.println("address : " + eElement.getTextContent());
                }
            }
            return importTemplateData;
        }
        return null;
    }

    // note - am making these all lower case as we want case insensitive checks
    private static void getSheetAndNamedRangesNamesQuicklyFromXLSX(String xlsxPath, Set<String> sheetNames, Set<String> namedRangesNames) throws Exception {
        OPCPackage opcPackage = OPCPackage.open(new File(xlsxPath));
        XSSFReader xssfReader = new XSSFReader(opcPackage);

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
//        System.out.println(IOUtils.toString(xssfReader.getWorkbookData()));
        Document workbookXML = builder.parse(xssfReader.getWorkbookData());
        workbookXML.getDocumentElement().normalize(); // probably fine on smaller XML, don't want to do on the big stuff
        NodeList nList = workbookXML.getElementsByTagName("definedName");
        for (int temp = 0; temp < nList.getLength(); temp++) {
            Node nNode = nList.item(temp);
            if (nNode.getNodeType() == Node.ELEMENT_NODE) {
                Element eElement = (Element) nNode;
                String nameName = eElement.getAttribute("name");
                if (nameName != null) {
                    namedRangesNames.add(nameName.toLowerCase());
                }
                //System.out.println("name : "  + eElement.getAttribute("name"));
                //System.out.println("address : " + eElement.getTextContent());
            }
        }
        nList = workbookXML.getElementsByTagName("sheet");
        for (int temp = 0; temp < nList.getLength(); temp++) {
            Node nNode = nList.item(temp);
            if (nNode.getNodeType() == Node.ELEMENT_NODE) {
                Element eElement = (Element) nNode;
                String sheetName = eElement.getAttribute("name");
                if (sheetName != null) {
                    sheetNames.add(sheetName.trim().toLowerCase()); // it seems it might need trimming
                }
                //System.out.println("name : "  + eElement.getAttribute("name"));
                //System.out.println("address : " + eElement.getTextContent());
            }
        }

    }

    public static void processSheet(
            StylesTable styles,
            ReadOnlySharedStringsTable strings,
            InputStream sheetInputStream, List<String> merges, POIEventDataRecipient poiEventDataRecipient)
            throws IOException, SAXException {
        Map<String, Integer> mergesByRowMap = null;
        if (merges != null) {
            // a merge looks like this D1:F1
            // for our purposes merges are only a row high so let's jam them in a map by the first cell with the number of additional cells
            mergesByRowMap = new HashMap<>();
            for (String merge : merges) {
                String[] split = merge.split(":");
                CellReference first = new CellReference(split[0]);
                CellReference second = new CellReference(split[1]);
                mergesByRowMap.put(split[0], second.getCol() - first.getCol());
            }
        }
        // poi convert - notably the iterators skip blank rows and cells hence the checking that indexes match
        DataFormatter formatter;
        formatter = new DataFormatter();
        //new DataFormatter(ZssContext.getCurrent().getLocale();
        InputSource sheetSource = new InputSource(sheetInputStream);
        SAXParserFactory saxFactory = SAXParserFactory.newInstance();
        try {
            SAXParser saxParser = saxFactory.newSAXParser();
            XMLReader sheetParser = saxParser.getXMLReader();
            ContentHandler handler = new XSSFSheetXMLHandler(
                    styles, strings, poiEventDataRecipient, formatter, false, mergesByRowMap);
            sheetParser.setContentHandler(handler);
            sheetParser.parse(sheetSource);
        } catch (ParserConfigurationException e) {
            throw new RuntimeException("SAX parser appears to be broken - " + e.getMessage());
        }
    }

    // will leave this internal for the mo and move out later

    /*

     */

    public static class MergedCellsHandler extends DefaultHandler {
        private final ArrayList<String> merges;

        public MergedCellsHandler(ArrayList<String> merges) {
            this.merges = merges;
        }

        public void startElement(String uri, String localName, String name, Attributes attributes) throws SAXException {
            if ("mergeCell".equals(name)) {
                merges.add(attributes.getValue("ref"));
            }
        }
    }
}