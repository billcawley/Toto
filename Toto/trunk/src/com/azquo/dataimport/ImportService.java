package com.azquo.dataimport;

import com.agilecrm.api.APIManager;
import com.azquo.*;
import com.azquo.admin.business.BusinessDAO;
import com.azquo.spreadsheet.CommonReportUtils;
import com.azquo.spreadsheet.ExcelService;
import com.azquo.spreadsheet.transport.HeadingWithInterimLookup;
import com.azquo.spreadsheet.zk.ChoicesService;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvParser;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.core.util.MultivaluedMapImpl;
import io.keikai.model.CellRegion;
import io.keikai.model.SSheet;
import net.lingala.zip4j.core.ZipFile;
import org.apache.log4j.Logger;
import org.apache.poi.poifs.crypt.Decryptor;
import org.apache.poi.poifs.crypt.EncryptionInfo;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.apache.poi.xssf.usermodel.*;
import org.springframework.ui.ModelMap;
import org.springframework.web.multipart.MultipartFile;
import org.w3c.dom.*;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;
/*import org.zkoss.poi.poifs.crypt.Decryptor;
import org.zkoss.poi.poifs.crypt.EncryptionInfo;
import org.zkoss.poi.poifs.filesystem.POIFSFileSystem;*/
import org.zeroturnaround.zip.ZipException;
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
import org.xml.sax.*;
import org.zeroturnaround.zip.ZipUtil;


import org.apache.poi.xssf.model.StylesTable;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.ss.format.CellDateFormatter;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.AreaReference;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.eventusermodel.ReadOnlySharedStringsTable;
import org.apache.poi.xssf.eventusermodel.XSSFReader;

import io.keikai.api.Range;
import io.keikai.api.Ranges;
import io.keikai.api.model.CellData;

import javax.servlet.http.HttpSession;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.xml.parsers.*;
import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.apache.poi.ss.usermodel.CellType.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.zkoss.zul.A;

/**
 * Copyright (C) 2016 Azquo Ltd.
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
 * <p>
 * todo : switch all poi references to use the latest version
 */

class JsonRule {
    String sourceTerm;
    String condition;
    String target;
    String format;
    List<String> found;

    JsonRule(String sourceTerm, String condition, String target, String format) {
        this.sourceTerm = sourceTerm;
        this.condition = condition;
        this.target = target;
        this.format = format;
        found = new ArrayList<>();

    }

}



public final class ImportService {

    public static final String dbPath = "/databases/";
    public static final String onlineReportsDir = "/onlinereports/";
    public static final String importTemplatesDir = "/importtemplates/";
    public static String LOCALIP = "127.0.0.1";
    private static final String IMPORTTEMPLATE = "importtemplate";
    public static final String IMPORTVERSION = "importversion";
    public static final String PREPROCESSOR = "preprocessor";
    public static final String WORKBOOK_PROCESSOR = "workbook processor";
    public static final String WORKBOOKPROCESSOR = "workbookprocessor";
    public static final String IMPORTMODEL = "Import Model";
    public static final String SHEETNAME = "sheet name";
    public static final String FILEENCODING = "fileencoding";
    public static final String JSONFIELDDIVIDER = "|";

    // todo - ignore Sheet1 etc and blank


    /* external entry point, moves the file to a temp directory in case pre processing is required
    (decompress or sheets in a book to individual csv files before sending to the db server).
     After that a little housekeeping.
     Added support for validation importing against a temporary copied database, a flag on
     last three params for Pending Uploads, perhaps could be consolidatesd? todo - pending uploads info
     parameters per file down to the file (not sheet!) check for parameters that have been set in a big chunk on the pending uploads screen
     */
    public static List<UploadedFile> importTheFile(final LoggedInUser loggedInUser, final UploadedFile uploadedFile, HttpSession session, PendingUploadConfig pendingUploadConfig) throws Exception { // setup just to flag it
        return importTheFile(loggedInUser, uploadedFile, session, pendingUploadConfig, null);
    }


    public static List<UploadedFile> importTheFile(final LoggedInUser loggedInUser, final UploadedFile uploadedFile, HttpSession session, PendingUploadConfig pendingUploadConfig, String userComment) throws Exception { // setup just to flag it
        String logId = System.currentTimeMillis() + (session != null ? session.getId() : "nosession") + uploadedFile.getFileName();
        SpreadsheetService.monitorLog(logId, loggedInUser.getBusiness().getBusinessName(), loggedInUser.getUser().getEmail(), "IMPORT", "START", uploadedFile.getFileName());
        if (session != null) {
            session.removeAttribute(ManageDatabasesController.IMPORTSTATUS);
        }
        loggedInUser.copyMode = false; // an exception might have left it true
        if ((userComment == null  || !userComment.startsWith("Report name = ")) && loggedInUser.getDatabase() == null) {
            throw new Exception("No database set");
        }
        /*
         * so, pending uploads will have an option to do data clearing before any import. This will apply validation or not, it's an execute
         * */
        if (pendingUploadConfig != null && pendingUploadConfig.getPendingDataClearCommand() != null) {
            System.out.println("*****");
            System.out.println("*****");
            System.out.println("*****");
            System.out.println("*****");
            System.out.print("Clear data before import ");
            // set user choices to file params, could be useful to the execute
            for (String choice : uploadedFile.getParameters().keySet()) {
                System.out.println(choice + " : " + uploadedFile.getParameter(choice));
                SpreadsheetService.setUserChoice(loggedInUser, choice, uploadedFile.getParameter(choice));
            }
            /*. so there's a bit of confusing logic which is worth flagging and perhaps tidying if it's not difficult. The importer and this check will look up
            a temporary copy explicitly server side based off the original name. When in copy mode it just accesses the copy and will fail if it's not there.
             This works but perhaps copyMode should be put on the data accesstoken and it will do it server side? todo
             */
            if (uploadedFile.isValidationTest()) {
                RMIClient.getServerInterface(loggedInUser.getDatabaseServer().getIp()).checkTemporaryCopyExists(loggedInUser.getDataAccessToken());
                loggedInUser.copyMode = true;
            }
            try {
                System.out.println(ReportExecutor.runExecute(loggedInUser, pendingUploadConfig.getPendingDataClearCommand(), null, uploadedFile.getProvenanceId(), false));
            } catch (Exception e) {
                e.printStackTrace();
            }
            System.out.println("*****");
            System.out.println("*****");
            System.out.println("*****");
            System.out.println("*****");
            loggedInUser.copyMode = false;
        }


        String fileName = uploadedFile.getFileName();
        String originalFilePath = uploadedFile.getPath();
        Path tempFile = Files.createTempFile(fileName.substring(0, fileName.length() - 4) + "_", fileName.substring(fileName.length() - 4));
        tempFile.toFile().deleteOnExit();
        Files.copy(Paths.get(uploadedFile.getPath()), tempFile, StandardCopyOption.REPLACE_EXISTING);
        uploadedFile.setPath(tempFile.toString()); // I'm now allowing adjustment of paths like this - having the object immutable became impractical
        List<UploadedFile> processedUploadedFiles = checkForCompressionAndImport(loggedInUser, uploadedFile, session, pendingUploadConfig, new HashMap<>(), userComment);
        if (!uploadedFile.isValidationTest()) {
            // persist on the database server
            SpreadsheetService.databasePersist(loggedInUser);
            // add to the uploaded list on the Manage Databases page
            // now jamming the import feedback in the comments
            String comments = ManageDatabasesController.formatUploadedFiles(processedUploadedFiles, -1, true, null);
            if (comments.length() > 10_000) {
                comments = comments.substring(0, 10_000) + " . . .";
            }
            UploadRecord uploadRecord = new UploadRecord(0, LocalDateTime.now(), loggedInUser.getUser().getBusinessId()
                    , loggedInUser.getDatabase().getId(), loggedInUser.getUser().getId()
                    , uploadedFile.getFileName() + (processedUploadedFiles.size() == 1 && processedUploadedFiles.get(0).getReportName() != null ? " - (" + processedUploadedFiles.get(0).getReportName() + ")" : ""), uploadedFile.getFileType() != null ? uploadedFile.getFileType() : "", comments, originalFilePath, userComment);
            UploadRecordDAO.store(uploadRecord);
            // and update the counts on the manage database page
            AdminService.updateNameAndValueCounts(loggedInUser, loggedInUser.getDatabase());
        } else {
            // important todo - SOMEHOW CHECK IF THE SOURCE DB WAS UPDATED IN THE MEAN TIME AND IF SO RUN THE VALIDATION AGAIN
            System.out.println("Zapping temporary copy");
            RMIClient.getServerInterface(loggedInUser.getDatabaseServer().getIp()).zapTemporaryCopy(loggedInUser.getDataAccessToken());
        }
        SpreadsheetService.monitorLog(logId, loggedInUser.getBusiness().getBusinessName(), loggedInUser.getUser().getEmail(), "IMPORT", "END", uploadedFile.getFileName());
        if (loggedInUser.getOpcPackage()!=null){
            loggedInUser.setPreprocessorLoaded(null);
            loggedInUser.getOpcPackage().revert();
            loggedInUser.setOpcPackage(null);
        }
        return processedUploadedFiles;
    }

    // deals with unzipping if required - recursive in case there's a zip in a zip
    private static List<UploadedFile> checkForCompressionAndImport(final LoggedInUser loggedInUser, final UploadedFile uploadedFile, HttpSession session, PendingUploadConfig pendingUploadConfig, HashMap<String, ImportTemplateData> templateCache, String userComment) throws Exception {
        List<UploadedFile> processedUploadedFiles = new ArrayList<>();
        if (uploadedFile.getFileName().toLowerCase().endsWith(".zip") || uploadedFile.getFileName().toLowerCase().endsWith(".7z")) {
            try {
                if (uploadedFile.getParameter("password") != null) {
                    File theFile = new File(uploadedFile.getPath());
                    ZipFile zf = new ZipFile(theFile);
                    if (zf.isEncrypted()) {
                        // do what ziputil explode does, extract into a directory of the same name
                        File tempFile = org.zeroturnaround.zip.commons.FileUtils.getTempFileFor(theFile);
                        org.zeroturnaround.zip.commons.FileUtils.moveFile(theFile, tempFile);
                        zf = new ZipFile(tempFile);
                        zf.setPassword(uploadedFile.getParameter("password"));
                        zf.extractAll(uploadedFile.getPath());
                    } else {
                        ZipUtil.explode(new File(uploadedFile.getPath()));
                    }
                } else {
                    ZipUtil.explode(new File(uploadedFile.getPath()));
                }
            } catch (ZipException ze) { // try for decrypt
            }
            // after exploding the original file is replaced with a directory
            File zipDir = new File(uploadedFile.getPath());
            zipDir.deleteOnExit();
            // todo - go to Files.list()?
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
            int counter = 1;
            /*

            So, we want to add support for adding a results file to a zip. That is to say a file saying
            "don't load these files" and "don't load these lines from a file we are loading"
            it will be called uploadreport.xlsx

*/
            Iterator<File> fileIterator = files.iterator();
            while (fileIterator.hasNext()) {
                File check = fileIterator.next();
                if (check.getName().equals("uploadreport.xlsx")) {
                    FileInputStream fs = new FileInputStream(check);
                    OPCPackage opcPackage = OPCPackage.open(fs);
                    XSSFWorkbook book = new XSSFWorkbook(opcPackage);
                    Sheet summarySheet = book.getSheet("Summary"); // literals not best practice, could it be factored between this and the xlsx file?
                    if (summarySheet != null) {
                        Cell topLeft = summarySheet.getRow(0).getCell(0);
                        if (topLeft != null) {
                            System.out.println("top left value " + topLeft.getStringCellValue());
                            if (topLeft.getStringCellValue().equals(uploadedFile.getFileName())) {
                                fileIterator.remove();
                                // we're assuming parameters per file not per sheet
                                Map<String, Map<String, String>> lookupValuesForFiles = new HashMap<>();
                                Map<Integer, Map<Integer, String>> fileRejectLines = new HashMap<>();
                                boolean parametersMode = false;
                                boolean readingRejectedLinesMode = false;
                                String fileName = null;
                                int sheetCounter = -1;
                                // file load means the index of "final" files to load that is to say the things read by readPreparedFile
                                Set<Integer> fileRejectFlags = new HashSet<>();
                                int lineSkipCol = -1; // as in which column has the lines we need to skip?
                                for (Row row : summarySheet) {
                                    if (readingRejectedLinesMode) {
                                        if (lineSkipCol == -1) {
                                            for (int col = 0; col < 100; col++) {
                                                if (row.getCell(col) != null && row.getCell(col).getCellType() == STRING
                                                        && row.getCell(col).getStringCellValue().equals("#")) {
                                                    lineSkipCol = col;
                                                    break;
                                                }
                                            }
                                        } else if (row.getCell(lineSkipCol) != null && row.getCell(lineSkipCol).getCellType() == NUMERIC) {
                                            // that blank string is used in other circumstances to store the value used to look up the line in the file. In other circumstances used to identify comments which isn't relevant here
                                            fileRejectLines.computeIfAbsent(sheetCounter, t -> new HashMap<>()).put(new Double(row.getCell(lineSkipCol).getNumericCellValue()).intValue(), "");
                                        }
                                    }
                                    if (row.getCell(0) == null || row.getCell(0).getStringCellValue().isEmpty()) {
                                        parametersMode = false;
                                    } else {
                                        if (row.getCell(0).getStringCellValue().equalsIgnoreCase(StringLiterals.PARAMETERS)) {
                                            parametersMode = true;
                                        } else if (row.getCell(0).getStringCellValue().equalsIgnoreCase(StringLiterals.MANUALLYREJECTEDLINES)) {
                                            lineSkipCol = -1;
                                            readingRejectedLinesMode = true;
                                        } else if (parametersMode) {
                                            lookupValuesForFiles.computeIfAbsent(fileName, t -> new HashMap<>()).put(row.getCell(0).getStringCellValue(), row.getCell(1).getStringCellValue());
                                        } else if (row.getCell(0).getStringCellValue().equalsIgnoreCase(uploadedFile.getFileName())) {// it's a line indicating the next (or first) file
                                            sheetCounter++;
                                            readingRejectedLinesMode = false;
                                            // if it goes down further levels this might get tripped up
                                            fileName = row.getCell(1).getStringCellValue();
                                            if (row.getCell(3) != null && StringLiterals.REJECTEDBYUSER.equalsIgnoreCase(row.getCell(3).getStringCellValue())) {
                                                fileRejectFlags.add(sheetCounter);
                                            }
                                        }
                                    }
                                }

                                // notably if pending upload config isn't null can we still do this?
                                if (pendingUploadConfig == null) {
                                    // going for a null on the clear command,
                                    pendingUploadConfig = new PendingUploadConfig(lookupValuesForFiles, fileRejectFlags, fileRejectLines, null);
                                }
                            }
                        }
                    }
                    opcPackage.close();
                }
            }

            for (File f : files) {
                if (files.size() > 1 && session != null) {
                    session.setAttribute(ManageDatabasesController.IMPORTSTATUS, counter + "/" + files.size());
                }
                if (!f.getName().equals("uploadreport.xlsx")) { // opposite of criteria above . . is this too hacky, should it be zapped or dealt with another way?
                    // need new upload file object now!
                    List<String> names = new ArrayList<>(uploadedFile.getFileNames());
                    names.add(f.getName());
                    Map<String, String> fileNameParams = new HashMap<>(uploadedFile.getParameters());
                    addFileNameParametersToMap(f.getName(), fileNameParams);
                    // bit hacky to stop the loading but otherwise there'd just be another map
                    if (pendingUploadConfig != null && pendingUploadConfig.getParametersForFile(f.getName()) != null) {
                        Map<String, String> parametersForFile = pendingUploadConfig.getParametersForFile(f.getName());
                        // don't change this to entries - the keys are converted to lower case
                        for (String key : parametersForFile.keySet()) {
                            fileNameParams.put(key.toLowerCase(), parametersForFile.get(key));
                        }
                    }
                    UploadedFile zipEntryUploadFile = new UploadedFile(f.getPath(), names, fileNameParams, false, uploadedFile.isValidationTest());
                    processedUploadedFiles.addAll(checkForCompressionAndImport(loggedInUser, zipEntryUploadFile, session, pendingUploadConfig, templateCache, userComment));
                }
                counter++;
            }
        } else { // nothing to decompress
            // simple checks in case the wrong type of file is being uploaded here - will probably be removed later
            String lcName = uploadedFile.getFileName().toLowerCase(Locale.ROOT);
            String suffix = lcName.substring(lcName.length()-4);
            if (lcName.startsWith(CreateExcelForDownloadController.USERSFILENAME) && (loggedInUser.getUser().isAdministrator() || loggedInUser.getUser().isMaster())) { // then it's not a normal import, users/permissions upload. There may be more conditions here if so might need to factor off somewhere
                uploadedFile.setError("Please upload the users file in the users tab.");
                processedUploadedFiles.add(uploadedFile);
            } else if (lcName.equals(CreateExcelForDownloadController.REPORTSCHEDULESFILENAME) && (loggedInUser.getUser().isAdministrator() || loggedInUser.getUser().isMaster())) {
                uploadedFile.setError("Please upload the report schedules file in the schedules tab.");
                processedUploadedFiles.add(uploadedFile);
            } else if (suffix.equals(".xls") || suffix.equals("xlsx")) {
                processedUploadedFiles.addAll(readBook(loggedInUser, uploadedFile, pendingUploadConfig, templateCache, userComment));
            } else if (suffix.equals(".jpg") || suffix.equals(".png")|| lcName.contains(StringLiterals.IMPORTDATA+"=")) {
                return Collections.singletonList(uploadImportTemplate(uploadedFile, loggedInUser, userComment));

            }else{
                //many SQL queries are encoded in ISO_8859_1
                String fileEncoding = checkFileEncoding(uploadedFile.getPath());
                if (fileEncoding!=null){
                    Map<String,String> parameters = uploadedFile.getParameters();
                    parameters.put(FILEENCODING, fileEncoding);
                    uploadedFile.setParameters(parameters);
                }
                processedUploadedFiles.add(readPreparedFile(loggedInUser, uploadedFile, false, pendingUploadConfig, templateCache));
            }
        }
        return processedUploadedFiles;
    }

    private static final Logger logger = Logger.getLogger(ImportService.class);


    // a book will be a report to upload or a workbook which has to be converted into a csv for each sheet
    private static List<UploadedFile> readBook(LoggedInUser loggedInUser, UploadedFile uploadedFile, PendingUploadConfig pendingUploadConfig, HashMap<String, ImportTemplateData> templateCache, String userComment) throws Exception {
        long time = System.currentTimeMillis();
        org.apache.poi.ss.usermodel.Workbook book;
        org.apache.poi.openxml4j.opc.OPCPackage opcPackage = null;
        boolean isImportData = false;
        if (uploadedFile.getFileName().toLowerCase(Locale.ROOT).contains(StringLiterals.IMPORTDATA+".")){
            isImportData = true;
        }
        // we now use apache POI which is faster than ZK but it has different implementations for .xls and .xlsx files
        try {
            // so, the try catches are there in case the file extension is incorrect. This has happened!
            if (uploadedFile.getFileName().toLowerCase().endsWith("xlsx")) {
                // is the opcpackage dangerous under windows - holding a file lock? Not sure . . . .
                try {
                    opcPackage = org.apache.poi.openxml4j.opc.OPCPackage.open(new FileInputStream(new File(uploadedFile.getPath())));
                    book = new org.apache.poi.xssf.usermodel.XSSFWorkbook(opcPackage);
                } catch (org.apache.poi.openxml4j.exceptions.OLE2NotOfficeXmlFileException ife) {
                    // Hanover may send 'em encrypted
                    POIFSFileSystem fileSystem = new POIFSFileSystem(new FileInputStream(uploadedFile.getPath()));
                    EncryptionInfo info = new EncryptionInfo(fileSystem);
                    Decryptor decryptor = Decryptor.getInstance(info);
                    String password = uploadedFile.getParameter("password") != null ? uploadedFile.getParameter("password") : "b0702"; // defaulting to an old Hanover password. Maybe zap . . .
                    if (!decryptor.verifyPassword(password)) { // currently hardcoded, this will change
                        throw new RuntimeException("Unable to process: document is encrypted.");
                    }
                    InputStream dataStream = decryptor.getDataStream(fileSystem);
                    book = new org.apache.poi.xssf.usermodel.XSSFWorkbook(dataStream);
                    //InputStream dataStream2 = decryptor.getDataStream(fileSystem);
                    //Files.copy(dataStream2, Paths.get("/home/edward/Downloads/test111.xlsx"));
                }
                //System.out.println("book open time " + (System.currentTimeMillis() - quicktest));
            } else {
                try {
                    book = new org.apache.poi.hssf.usermodel.HSSFWorkbook(new FileInputStream(new File(uploadedFile.getPath())));
                } catch (Exception problem) {
                    throw new Exception("unable to open irregular xls file, please resave as an xlsx file");
                    // unfortunately this libreoffice conversion is not reliable
                    /*try {
                        String libreofficecommand = SystemUtils.IS_OS_WINDOWS ? "C:\\Program Files\\LibreOffice\\program\\soffice.exe" : "libreoffice";
                        logger.warn("POI can't read that " + uploadedFile.getPath() + ", attempting conversion with libre office . . .");
                        logger.warn(libreofficecommand + " --headless --convert-to xlsx:\"Calc MS Excel 2007 XML\" --outdir \"" + Paths.get(uploadedFile.getPath()).getParent().toString() + "\" \"" + uploadedFile.getPath() + "\"");
                        // so, the xls could be non standard, try to get libre office to convert it - requires of course that libre office is installed!
                        String[] commandArray = new String[]{
                                libreofficecommand
                                , "--headless"
                                , "--convert-to"
                                , "xlsx:Calc MS Excel 2007 XML"
                                , "--outdir"
                                , Paths.get(uploadedFile.getPath()).getParent().toString()
                                , uploadedFile.getPath()
                        };

                        CommandLineCalls.runCommand(null, commandArray, true, null);
                        // ok try to read the converted file!
                        // opcpackage dangerous for filehandling under windows??
                        opcPackage = OPCPackage.open(new File(uploadedFile.getPath() + "x"));
                        book = new XSSFWorkbook(opcPackage);
                    } catch (Exception e) {
                        throw new Exception("unable to fix irregular xls file");
                    }*/
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            uploadedFile.setError(e.getMessage());
            if (pendingUploadConfig != null) {
                pendingUploadConfig.incrementFileCounter();
            }
            if (opcPackage != null) opcPackage.revert();
            return Collections.singletonList(uploadedFile);
        }
        if (!uploadedFile.isValidationTest()) {
            for (int sheetNo = 0; sheetNo < book.getNumberOfSheets(); sheetNo++) {
                org.apache.poi.ss.usermodel.Sheet sheet = book.getSheetAt(sheetNo);
                if (sheet.getSheetName().equalsIgnoreCase(ImportService.IMPORTMODEL)) {
                    if ((loggedInUser.getUser().isAdministrator() || loggedInUser.getUser().isDeveloper())) {
                        if (opcPackage != null) opcPackage.revert();
                        return Collections.singletonList(uploadImportTemplate(uploadedFile, loggedInUser, userComment));
                    }
                }
                // Import Model is one way to detect a template but it's not always there - names beginning az_Headings are a sign also
                for (org.apache.poi.ss.usermodel.Name name : BookUtils.getNamesForSheet(sheet)) {
                    if (name.getNameName().startsWith(AZHEADINGS)) {
                        if (opcPackage != null) opcPackage.revert();
                        return Collections.singletonList(uploadImportTemplate(uploadedFile, loggedInUser, userComment));
                    }
                }
            }
            // also just do a simple check on the file name.  Allow templates to be included in a setup bundle then directed correctly
            String lcName = uploadedFile.getFileName().toLowerCase();
            if ((lcName.contains("import templates")  || lcName.contains(PREPROCESSOR) || lcName.contains(WORKBOOK_PROCESSOR) || lcName.contains("headings")) && !lcName.contains("=")) {
                if ((loggedInUser.getUser().isAdministrator() || loggedInUser.getUser().isDeveloper())) {
                    if (opcPackage != null) opcPackage.revert();
                    //preprocessors are not assigned to the file, import templates are assigned
                    return Collections.singletonList(uploadImportTemplate(uploadedFile, loggedInUser, userComment));
                }
            }

            // on an upload file, should this file be flagged as one that moves with backups and is available for non admin users to download
            org.apache.poi.ss.usermodel.Name fileTypeRange = BookUtils.getName(book, StringLiterals.AZFILETYPE);
            if (fileTypeRange != null) {
                CellReference sheetNameCell = BookUtils.getNameCell(fileTypeRange);
                if (sheetNameCell != null) {
                    try {
                        String fileType = book.getSheet(fileTypeRange.getSheetName()).getRow(sheetNameCell.getRow()).getCell(sheetNameCell.getCol()).getStringCellValue();
                        if (fileType != null) {
                            // note - this means this will only kick in in s single XLSX upload not a zip of them
                            uploadedFile.setFileType(fileType);
                        }
                    } catch (Exception e) {
                        logger.warn("no file type in az_FileType");
                    }
                }
            }


            String reportName = null;
            // a misleading name now we have the ImportTemplate object
            org.apache.poi.ss.usermodel.Name reportRange = BookUtils.getName(book, StringLiterals.AZREPORTNAME);
            if (userComment!=null && userComment.startsWith("Report name = ") || reportRange != null) {
                if (reportRange != null) {
                    CellReference sheetNameCell = BookUtils.getNameCell(reportRange);
                    if (sheetNameCell != null) {
                        reportName = book.getSheet(reportRange.getSheetName()).getRow(sheetNameCell.getRow()).getCell(sheetNameCell.getCol()).getStringCellValue();
                    }
                }else{
                    reportName = userComment.substring("Report name = ".length());
                }
            }
            if (reportName != null) {
                if ((loggedInUser.getUser().isAdministrator() || loggedInUser.getUser().isDeveloper())) {
                    uploadReport(loggedInUser,reportName,uploadedFile);
                     uploadedFile.setProcessingDuration(System.currentTimeMillis() - time);
                    if (opcPackage != null) opcPackage.revert();
                    return Collections.singletonList(uploadedFile);
                }
            }
        }

        if (loggedInUser.getDatabase() == null) {
            uploadedFile.setError("no database set");
            if (opcPackage != null) opcPackage.revert();
            return Collections.singletonList(uploadedFile);
        }
         // add a share of the initial excel load time to each of the sheet convert and processing times
        long sheetExcelLoadTimeShare = (System.currentTimeMillis() - time) / book.getNumberOfSheets();
        // with more than one sheet to convert this is why the function returns a list
        List<UploadedFile> toReturn = new ArrayList<>();
        Map<String,String> paras = uploadedFile.getParameters();
        //new behaviour June 2022 - if there exists a preprocessor with a given name which matches part of the input file name, and no preprocessor is specified, then use it.
        if (paras.get(PREPROCESSOR)==null && paras.get(WORKBOOKPROCESSOR)==null) {
            List<ImportTemplate> forBusinessId = ImportTemplateDAO.findForBusinessId(loggedInUser.getBusiness().getId());
            for (ImportTemplate importTemplate:forBusinessId) {
                String file = importTemplate.getTemplateName();
                int ppPos = file.indexOf(PREPROCESSOR);
                if (ppPos > 0) {
                    if (uploadedFile.getFileName().toLowerCase(Locale.ROOT).contains(file.substring(0, ppPos).trim().toLowerCase(Locale.ROOT))) {
                        paras.put(PREPROCESSOR, file);
                        uploadedFile.setParameters(paras);
                        break;
                    }
                }
                int wpPos = file.indexOf(WORKBOOK_PROCESSOR);
                if (wpPos> 0 && uploadedFile.getFileName().toLowerCase(Locale.ROOT).contains(file.substring(0, wpPos).trim().toLowerCase(Locale.ROOT))) {
                    paras.put(WORKBOOKPROCESSOR, file);
                    uploadedFile.setParameters(paras);
                    break;
                }
            }
        }

        // check for whether the book should be transformed using the workbook processor - a bit like the preprocessor but all in one shot
    /* For Bonza - check the Bonza Appraisal Preprocessor
       The first sheet gives a list of ranges that will be transferred individually to other sheets in the workbook.  Formulae will be executed,
       and any sheet labelled 'output' will be output - assuming here no further need for import templates (though these could be added)

     */
        String preprocessor = uploadedFile.getParameter(WORKBOOKPROCESSOR);
        if (preprocessor!= null) {
            if (!preprocessor.toLowerCase(Locale.ROOT).endsWith(".xlsx")){
                preprocessor +=".xlsx";
            }
            ImportTemplate preProcess = ImportTemplateDAO.findForNameAndBusinessId(preprocessor, loggedInUser.getUser().getBusinessId());
            if (preProcess != null) {
                String workbookProcessor = SpreadsheetService.getHomeDir() + dbPath + loggedInUser.getBusinessDirectory() + importTemplatesDir + preProcess.getFilenameForDisk();
                OPCPackage opcPackage1;
                try (FileInputStream fi = new FileInputStream(workbookProcessor)) { // this will hopefully guarantee that the file handler is released under windows
                    opcPackage1 = OPCPackage.open(fi);
                } catch (Exception e) {
                    e.printStackTrace();
                    throw new Exception("Cannot load workbook processor from " + workbookProcessor);
                }
                Workbook ppBook = new XSSFWorkbook(opcPackage1);
                // need to set values, need to be careful of what space there is on the output book
                Sheet ppSheet = ppBook.getSheetAt(0);
                org.apache.poi.ss.usermodel.Name workbookNameRegion = BookUtils.getName(ppBook, "az_workbookname");

                if (workbookNameRegion!=null){
                    AreaReference wname = new AreaReference(workbookNameRegion.getRefersToFormula(), null);
                    ppSheet.getRow(wname.getFirstCell().getRow()).getCell(wname.getFirstCell().getCol()).setCellValue(uploadedFile.getFileName());

                }
                int rowNo = 1;
                String error ="";
                while (ppSheet.getRow(rowNo)!=null){
                    try{
                        oneRange(ppBook, ppSheet.getRow(rowNo++),book);
                    }catch(Exception e){
                        error += e.getMessage() + ",";//to do - show errors
                    }

                }

                XSSFFormulaEvaluator.evaluateAllFormulaCells(ppBook);
                for (int sheetNo = 0; sheetNo < ppBook.getNumberOfSheets(); sheetNo++) {
                    org.apache.poi.ss.usermodel.Sheet sheet = ppBook.getSheetAt(sheetNo);
                    if (sheet.getSheetName().toLowerCase().contains("output")) {
                        UploadedFile uploadedFile1 = readSheet(loggedInUser, uploadedFile, sheet, sheet.getSheetName(), pendingUploadConfig, templateCache, userComment);
                        uploadedFile1.addToProcessingDuration(sheetExcelLoadTimeShare);
                        toReturn.add(uploadedFile1);
                    }
                }
                opcPackage1.revert();
                int k=1;
                if (k==0) {
                    //debug lines
                    String oFile = "c:\\users\\test\\Downloads\\Corrupt.xlsx";
                    File wFile = new File(oFile);
                    wFile.delete(); // to avoid confusion

                    OutputStream outputStream = new FileOutputStream(wFile);
                    ppBook.write(outputStream);
                }

            }
        } else { // normal
            for (int sheetNo = 0; sheetNo < book.getNumberOfSheets(); sheetNo++) {
                org.apache.poi.ss.usermodel.Sheet sheet = book.getSheetAt(sheetNo);
                if (!book.isSheetHidden(sheetNo)) {
                    String sheetName = sheet.getSheetName();
                    if (isImportData){//converting each sheet into a csv import
                        sheetName = "("+StringLiterals.IMPORTDATA + "=" + sheetName + ")";
                    }
                    UploadedFile uploadedFile1 = readSheet(loggedInUser, uploadedFile, sheet, sheetName,pendingUploadConfig, templateCache, userComment);
                    uploadedFile1.addToProcessingDuration(sheetExcelLoadTimeShare);
                    toReturn.add(uploadedFile1);
                }
            }
        }

        if (opcPackage != null) opcPackage.revert();
        return toReturn;
    }

    public static void uploadReport(LoggedInUser loggedInUser, String reportName, UploadedFile uploadedFile)throws Exception{
        uploadedFile.setDataModified(true); // ok so it's not technically data modified but the file has been processed correctly. The report menu will have been modified
        int businessId = loggedInUser.getUser().getBusinessId();
        int databaseId = loggedInUser.getDatabase().getId();
        String pathName = loggedInUser.getBusinessDirectory();
        // used to only overwrite if uploaded by this user, we;ll go back to replacing one for the same business
        //OnlineReport or = OnlineReportDAO.findForNameAndUserId(reportName, loggedInUser.getUser().getId());
        OnlineReport or = OnlineReportDAO.findForNameAndBusinessId(reportName, loggedInUser.getUser().getBusinessId());
/* todo - category names here as with the backup restore why was this commented? Just never used?
                    String category = null;
                    if (fileName.contains(CATEGORYBREAK)) {
                        category = fileName.substring(0, fileName.indexOf(CATEGORYBREAK));
                        fileName = f.getName().substring(fileName.indexOf(CATEGORYBREAK) + CATEGORYBREAK.length());
                    }
                    */
        if (or != null) {
            // zap the old one first
            try {
                Files.deleteIfExists(Paths.get(SpreadsheetService.getHomeDir() + dbPath + pathName + onlineReportsDir + or.getFilenameForDisk()));
            } catch (Exception e) {
                System.out.println("problem deleting old report");
                e.printStackTrace();
            }
            or.setFilename(uploadedFile.getFileName()); // it might have changed, I don't think much else under these circumstances
            or.setUserId(loggedInUser.getUser().getId());
        } else {
            or = new OnlineReport(0, LocalDateTime.now(), businessId, loggedInUser.getUser().getId(), loggedInUser.getDatabase().getName(), reportName, uploadedFile.getFileName(), "", "");
        }
        OnlineReportDAO.store(or); // store before or.getFilenameForDisk() or the id will be wrong!
       // List<org.apache.poi.ss.usermodel.Name> names = (List<Name>)book.getAllNames();

        Path fullPath = Paths.get(SpreadsheetService.getHomeDir() + dbPath + pathName + onlineReportsDir + or.getFilenameForDisk());
        Files.createDirectories(fullPath.getParent()); // in case it doesn't exist
        Files.copy(Paths.get(uploadedFile.getPath()), fullPath); // and copy
        DatabaseReportLinkDAO.link(databaseId, or.getId());
        // ---- end report uploading
        uploadedFile.setReportName(reportName);

    }


    private static String getMapValue(Map<String,String>mapping, String value){
        try {
            String found = mapping.get(value);
            if (found!=null){
                return found;
            }
            return "";
        }catch(Exception e){
            return "";
        }
    }

    private static void zapValuesInSheet(Sheet outputSheet){
        for (int rowNo = 0;rowNo <= outputSheet.getLastRowNum();rowNo++){
            Row row = outputSheet.getRow(rowNo);
            if (row != null) {
                for (int colNo = row.getFirstCellNum(); colNo <= row.getLastCellNum(); colNo++) {
                    Cell cell = row.getCell(colNo);
                    if (cell != null) {
                        cell.setCellValue("");
                    }
                }
            }
        }

    }

    private static void copyRange(Cell firstCell, int rows, int cols, Sheet outputSheet){
         int fRow = firstCell.getRowIndex();
        int lRow = fRow + rows;
        int olRow = outputSheet.getLastRowNum();
        Sheet inputSheet = firstCell.getSheet();
        int ofRow = 0;
        for (int iRow = fRow; iRow <= lRow; iRow++) {
            Row inputRow = inputSheet.getRow(iRow);
            int oRow = iRow - fRow;
            if (oRow < olRow) {
                Row outputRow = outputSheet.getRow(oRow);
                int fCell = firstCell.getColumnIndex();
                int lCell = fCell + cols;
                for (int iCell = fCell; iCell < lCell; iCell++) {
                    Cell cell = inputRow.getCell(iCell);
                    Cell oCell = outputRow.getCell(iCell - fCell);
                    if (cell != null) {//don't bother to fill in cells that do not already exist
                        switch (cell.getCellType()) {
                            case BLANK:
                                oCell.setBlank();
                                break;
                            case BOOLEAN:
                                oCell.setCellValue(cell.getBooleanCellValue());
                                break;
                            case ERROR:
                                oCell.setCellErrorValue(cell.getErrorCellValue());
                                break;
                            case FORMULA:
                                try {
                                    oCell.setCellValue(cell.getNumericCellValue());
                                }catch(Exception e){
                                    try{
                                        oCell.setCellValue(cell.getStringCellValue());
                                    }catch(Exception e2){
                                        oCell.setCellValue("");
                                    }
                                }
                                break;
                            case NUMERIC:
                                oCell.setCellValue(cell.getNumericCellValue());
                                break;
                            case STRING:
                                oCell.setCellValue(cell.getStringCellValue());
                                break;
                            default:
                                oCell.setCellFormula(cell.getCellFormula());
                        }
                    }
                }
            }
        }
    }

    private static int getIntorNull(Cell cell){
        if (cell==null){
            return 0;
        }
        return (int) cell.getNumericCellValue();
    }


    private static void oneRange(Workbook ppBook, Row row, Workbook inputBook)throws Exception{
        String rangeName = row.getCell(0).getStringCellValue().trim();
        String sheetName = row.getCell(1).getStringCellValue().trim();
        String[] anchors = null;
        Sheet sheet = inputBook.getSheet(sheetName);
        if (sheet == null){
            throw new Exception ("cannot find " + sheetName);
        }
        String text = null;
        try{

            String anchor = row.getCell(2).getStringCellValue().trim();
            anchors = anchor.split("\\|");

            if (anchor.startsWith("[")&& anchor.endsWith("]")) {
                //anchor is the text in a shape
                XSSFDrawing drawing = (XSSFDrawing) sheet.getDrawingPatriarch();

                for (XSSFShape shape : drawing.getShapes()) {
                    // possible:   XSSFConnector, XSSFGraphicFrame, XSSFPicture, XSSFShapeGroup, XSSFSimpleShape
                    if (shape.getShapeName().equalsIgnoreCase(anchor.substring(1, anchor.length() - 1))) {
                        XSSFSimpleShape sshape = (XSSFSimpleShape) shape;
                        text =  sshape.getText();
                        break;
                    }
                }
            }
        }catch(Exception e){
            //anchor is null!
        }
        Cell found = null;
        Cell topLeft= null;
        int endRow = 0;
        int columns = 1;
        if (text==null) {
            int rowOffset = getIntorNull(row.getCell(3));
            int colOffset = getIntorNull(row.getCell(4));
            columns = getIntorNull(row.getCell(5));
            String[] endRowStrings = null;
            String endRowString = null;
            Cell endCell = row.getCell(6);
            if (endCell!=null && endCell.getCellType()!=CellType.BLANK){
                 try{
                    endRowString = row.getCell(6).getStringCellValue().trim();
                    endRowStrings = endRowString.split("\\|");

                }catch(Exception e){
                    endRow = getIntorNull(row.getCell(6));
                }
           }

            if (anchors != null) {
                found = findCell(inputBook, sheetName, anchors);
                if (found == null) {
                    throw new Exception("cannot find anchor " + anchors[0]);
                }
                topLeft = sheet.getRow(found.getRowIndex() + rowOffset).getCell(found.getColumnIndex() + colOffset);
            } else {
                topLeft = sheet.getRow(rowOffset - 1).getCell(colOffset - 1);
            }
            if (endRow == 0 && text == null) {
                for (endRow = found.getRowIndex(); endRow <= topLeft.getSheet().getLastRowNum(); endRow++) {
                    if (sheet.getRow(endRow) != null) {
                        Cell cell = sheet.getRow(endRow).getCell(topLeft.getColumnIndex());
                        if ((endRowStrings == null && cell == null) || (cell != null && cell.getCellType() == STRING && tryall(cell.getStringCellValue().trim(),endRowStrings))) {
                            break;
                        }
                    }
                }
                if (endRow > topLeft.getSheet().getLastRowNum()) {
                    endRow = 0;
                }
            }
        }
        Sheet outputSheet = ppBook.getSheet(rangeName);
        zapValuesInSheet(outputSheet);
        if (endRow > 0 || text != null) {
             if (outputSheet != null) {
                if (text != null) {
                    outputSheet.getRow(0).getCell(0).setCellValue(text);
                } else {
                    copyRange(topLeft, endRow - topLeft.getRowIndex(), columns, outputSheet);
                }
            }
         }
    }


    private static Cell findCell(Workbook book, String sheetName, String[] cellVals){
        if (sheetName == null){
            for (Sheet sheet:book){
                Cell  cell = findCell(book, sheet.getSheetName(), cellVals);
                if(cell!=null){
                    return cell;
                }
            }
        }
        Sheet sheet = book.getSheet(sheetName);
        for (int rowNo= 0; rowNo<=sheet.getLastRowNum();rowNo++){
            if (sheet.getRow(rowNo)!= null){
                Row row = sheet.getRow(rowNo);
                for (int cellNo= row.getFirstCellNum();cellNo<row.getLastCellNum();cellNo++){
                    if (row.getCell(cellNo)!=null && row.getCell(cellNo).getCellType()== STRING && tryall(row.getCell(cellNo).getStringCellValue().trim(), cellVals)){
                        return row.getCell(cellNo);
                    }
                }
            }

        }
        return null;
    }

    private static boolean tryall(String toTest, String[] possibles){
        if(possibles==null){
            return false;
        }
        for (String possible:possibles){
            if (toTest.equalsIgnoreCase(possible)){
                return true;
            }
        }
        return false;
    }

    public static List<List<String>> rangeToList(io.keikai.api.model.Sheet sheet, CellRegion regionName){
        List<List<String>> toReturn = new ArrayList<>();
        int startRow = 0;
        int startCol = 0;
        int endRow = sheet.getLastRow();
        int endCol = sheet.getLastColumn(0);
        if (regionName != null) {
            startRow = regionName.getRow();
            endRow = regionName.getLastRow();
            startCol = regionName.getColumn();
            endCol = regionName.getLastColumn();
        }
        for (int rNo = startRow; rNo <= endRow; rNo++) {
            List<String>dataline = new ArrayList<>();
            for (int cNo = startCol; cNo <= endCol; cNo++) {
                dataline.add(zapCRs(getCellValue(sheet,rNo,cNo).getString()));
            }
            toReturn.add(dataline);
        }
        return toReturn;
    }




    public static void rangeToCSV(io.keikai.api.model.Sheet sheet, AreaReference areaReference, CsvWriter csvW) throws Exception {
        int startRow = areaReference.getFirstCell().getRow();
        int endRow = areaReference.getLastCell().getRow();
        int startCol = areaReference.getFirstCell().getCol();
        int endCol = areaReference.getLastCell().getCol();
        for (int rNo = startRow; rNo <= endRow; rNo++) {
            for (int cNo = startCol; cNo <= endCol; cNo++) {
                String val = getCellValue(sheet, rNo, cNo).getString();
                csvW.write(zapCRs(val));
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
                    if (templateSource.get(rNo).size() > cNo) {
                        val = templateSource.get(rNo).get(cNo);
                    }
                }
                if (knownNames != null) {
                    for (Map.Entry<String, String> knownNameValue : knownNames.entrySet()) {
                        val = val.replaceAll("`" + knownNameValue.getKey() + "`", knownNameValue.getValue());
                    }
                }
                csvW.write(zapCRs(val));
            }
            csvW.endRecord();
        }
    }


    public static Map<String,String> rangeToMap(Workbook book, Name name, int column) throws Exception {
        Map<String,String> toReturn = new HashMap<>();
        Sheet sheet = book.getSheet(name.getSheetName());
        AreaReference areaReference = new AreaReference(name.getRefersToFormula(), null);
        int startRow = areaReference.getFirstCell().getRow();
        int endRow = areaReference.getLastCell().getRow();
        int startCol = areaReference.getFirstCell().getCol();
        int endCol = areaReference.getLastCell().getCol();
        if (endCol == startCol) return toReturn;
        for (int rNo = startRow; rNo <= endRow; rNo++) {
            String parameterName = getCellValue(sheet,rNo, startCol);
            String parameterValue = getCellValue(sheet,rNo, startCol + column);
            if (parameterName.length()>0){
                 toReturn.put(parameterName.toLowerCase(Locale.ROOT), parameterValue);
            }
        }
        return toReturn;
    }



    private static UploadedFile readSheet(LoggedInUser loggedInUser, UploadedFile uploadedFile, org.apache.poi.ss.usermodel.Sheet sheet, String sheetName, PendingUploadConfig pendingUploadConfig, HashMap<String, ImportTemplateData> templateCache, String userComment) {
        boolean usDates = false;
        if (uploadedFile.getParameter("usdates")!=null){
            usDates = true;
        }
        long time = System.currentTimeMillis();
        try {
            long test = System.currentTimeMillis();
            File temp = File.createTempFile(uploadedFile.getPath() + sheetName, ".csv");
            String tempPath = temp.getPath();
            temp.deleteOnExit();
            FileOutputStream fos = new FileOutputStream(tempPath);
            CsvWriter csvW = new CsvWriter(fos, '\t', StandardCharsets.UTF_8);
            csvW.setUseTextQualifier(false);
            // poi convert - notably the iterators skip blank rows and cells hence the checking that indexes match
            int rowIndex = -1;
            boolean emptySheet = true;
            for (org.apache.poi.ss.usermodel.Row row : sheet) {
                emptySheet = false;
                // turns out blank lines are important
                if (++rowIndex != row.getRowNum()) {
                    while (rowIndex != row.getRowNum()) {
                        csvW.endRecord();
                        rowIndex++;
                    }
                }
                int cellIndex = -1;
                for (Iterator<org.apache.poi.ss.usermodel.Cell> ri = row.cellIterator(); ri.hasNext(); ) {
                    org.apache.poi.ss.usermodel.Cell cell = ri.next();
                    if (++cellIndex != cell.getColumnIndex()) {
                        while (cellIndex != cell.getColumnIndex()) {
                            csvW.write("");
                            cellIndex++;
                        }
                    }
                    final String cellValue = getCellValueUS(cell, usDates);
                    csvW.write(zapCRs(cellValue));
                }
                csvW.endRecord();
            }
            csvW.close();
            fos.close();
            //System.out.println("current sheet to csv on " + sheetName + " " + (System.currentTimeMillis() - test));
            long convertTime = System.currentTimeMillis() - time;
            List<String> names = new ArrayList<>(uploadedFile.getFileNames());
            names.add(sheetName);
            // the sheet name might have parameters - try to get them
            Map<String, String> fileNameParams = new HashMap<>(uploadedFile.getParameters());
            addFileNameParametersToMap(sheetName, fileNameParams);
            // reassigning uploaded file so the correct object will be passed back on exception
            fileNameParams.put(SHEETNAME, sheetName);
            uploadedFile = new UploadedFile(tempPath, names, fileNameParams, true, uploadedFile.isValidationTest()); // true, it IS converted from a worksheet
            if (emptySheet) {
                if (pendingUploadConfig != null) {
                    pendingUploadConfig.incrementFileCounter();
                }
                uploadedFile.setError("Empty sheet : " + sheetName);
                return uploadedFile;
            } else {
                if (sheetName.contains(StringLiterals.IMPORTDATA +"=")){
                    UploadedFile toReturn = uploadImportTemplate(uploadedFile,loggedInUser,userComment);
                    toReturn.addToProcessingDuration(convertTime);
                    return toReturn;
                }
                UploadedFile toReturn = readPreparedFile(loggedInUser, uploadedFile, false, pendingUploadConfig, templateCache);
                // the UploadedFile will have the database server processing time, add the Excel stuff to it for better feedback to the user
                toReturn.addToProcessingDuration(convertTime);
                return toReturn;
            }
        } catch (Exception e) {
            e.printStackTrace();
            //Throwable t = e;
            //while (t.getCause() != null) { // unwrap as much as possible
            //    t = t.getCause();
            //}
            //it turns out the e has a better message ('Error evaluating ...  ' rather than 'IFERROR')
            uploadedFile.setError(e.getMessage());
            if (pendingUploadConfig != null) {
                pendingUploadConfig.incrementFileCounter();
            }
            return uploadedFile;
        }
    }


    // things that can be read from "Parameters" in an import template sheet
    public static final String POSTPROCESSOR = "postprocessor";
    public static final String PENDINGDATACLEAR = "pendingdataclear";
    private static final String VALIDATION = "validation";
    private static final String NOFILEHEADINGS = "nofileheadings";
    private static final String LANGUAGE = "language";
    private static final String SKIPLINES = "skiplines";
    private static final String AZHEADINGS = "az_Headings";
    public static final String SHEETNAMECONTAINS = "sheetnamecontains";
    public static final String SHEETNAMEMATCHES = "sheetnamematches";


    // copy the file to the database server if it's on a different physical machine then tell the database server to process it
    private static UploadedFile readPreparedFile(final LoggedInUser loggedInUser, UploadedFile uploadedFile, boolean importTemplateUsedAlready, PendingUploadConfig pendingUploadConfig, HashMap<String, ImportTemplateData> templateCache) throws
            Exception {
        if (pendingUploadConfig != null) {
            //System.out.println("upload name  " + uploadedFile.getFileName() + " puc counter " + pendingUploadConfig.getFileCount());
            if (pendingUploadConfig.isFileToReject()) {
                pendingUploadConfig.incrementFileCounter();
                uploadedFile.setError(StringLiterals.REJECTEDBYUSER);
                return uploadedFile;
            }
            if (pendingUploadConfig.getFileRejectLines() != null) {
                uploadedFile.setIgnoreLines(pendingUploadConfig.getFileRejectLines());
            }
        }
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
        String preProcessor = null;
        if (uploadedFile.getParameter(PREPROCESSOR) != null && uploadedFile.getParameter(IMPORTVERSION) == null) {
            try {
                preProcessor = uploadedFile.getParameter(PREPROCESSOR);
                //maybe import version is second last word (as in `thistype otherword claims preprocessor')
                int word2End = preProcessor.lastIndexOf(" ");
                int word2Start = preProcessor.lastIndexOf(" ", word2End - 1);
                Map<String, String> parameters = uploadedFile.getParameters();
                parameters.put(IMPORTVERSION, preProcessor.substring(word2Start + 1, word2End));
                uploadedFile.setParameters(parameters);
            } catch (Exception e) {
                //cannot guess importversion
            }

        }

        String importVersion = uploadedFile.getParameter(IMPORTVERSION);
        if (uploadedFile!=null && !templateName.toLowerCase().startsWith("sets") && !importTemplateUsedAlready) {
            ImportTemplateData importTemplateData = getImportTemplateForUploadedFile(loggedInUser, uploadedFile, templateCache);
            if (importTemplateData != null) {
                templateName = findRelevantTemplate(loggedInUser,uploadedFile, importTemplateData, templateName);
                Map<String,String>templateParameters = new HashMap<>();
                if (uploadedFile.getParameters() == null || uploadedFile.getParameters().size()==0) {

                    templateParameters = getTemplateParameters(importTemplateData.getSheets().get(templateName)); // things like pre processor, file encoding etc
                }else{
                    templateParameters.put(PREPROCESSOR, preProcessor);
                }

                List<List<String>> standardHeadings = new ArrayList<>();
                // scan first for the main model
                // scan first for the main model
                List<List<String>> template;
                boolean hasImportModel = false;
                if (importVersion != null) {
                    String importModel = ImportService.IMPORTMODEL;
                    // new logic - an import version's parameters can override the import model
                    List<List<String>> versionSheet = sheetInfo(importTemplateData, importVersion);//case insensitive
                    if (versionSheet != null) {
                        Map<String, String> versionParameters = scanJustParameters(versionSheet);
                        if (versionParameters.get("importmodel") != null) {
                            importModel = versionParameters.get("importmodel"); // it may set it to "none" to bock an import model
                        }
                    } else {
                        throw new Exception("Import version " + importVersion + " not found in import template.");
                    }

                    template = importTemplateData.getSheets().get(importModel);
                    if (template == null) {
                        template = sheetInfo(importTemplateData, importVersion);//case insensitive
                    } else {
                        hasImportModel = true;
                    }
                } else {
                    template = sheetInfo(importTemplateData, templateName);//case insensitive
                }
                if (template != null) {
                    importSheetScan(template, null, standardHeadings, null, templateParameters, null);
                }

                // without standard headings then there's nothing to work with
                if (!standardHeadings.isEmpty()) {
                    Map<RowColumn, String> topHeadings = new HashMap<>();
                    // specific headings on the file we're loading
                    List<List<String>> versionHeadings = new ArrayList<>();
                    // this *should* be single line, used to lookup information from the Import Model
                    List<List<String>> headingReference = new ArrayList<>();
                    // a "version" - similar to the import model parsing but the headings can be multi level (double decker) and not at the top thus allowing for top headings
                    // unlike the "default" mode there can be a named range for the headings here so
                    // todo EFC double check logic here for import version without import model
                    AreaReference headingsName = importTemplateData.getName(AZHEADINGS + importVersion);
                    if (hasImportModel || headingsName != null) {
                        template = sheetInfo(importTemplateData, importVersion);
                        if (template != null) {
                            if (headingsName != null) { // we have to have it or don't bother!
                                uploadedFile.setSkipLines(headingsName.getFirstCell().getRow());
                                uploadedFile.setHeadingDepth((headingsName.getLastCell().getRow() - headingsName.getFirstCell().getRow()) + 1);
                                // parameters and lookups are cumulative, pass through the same maps
                                importSheetScan(template, topHeadings, headingReference, versionHeadings, templateParameters, headingsName);
                            } else {
                                throw new Exception("Import version sheet " + importVersion + " found but couldn't find az_Headings" + importVersion);
                            }
                        }
                    }
                    uploadedFile.setTemplateParameters(templateParameters);
                    if (templateParameters.get(SHEETNAMECONTAINS) != null && uploadedFile.getParameter(SHEETNAME) != null) {
                        String sheetNameContains = templateParameters.get(SHEETNAMECONTAINS).toLowerCase().trim();

                        if (!uploadedFile.getParameter(SHEETNAME).toLowerCase().contains(sheetNameContains)) {
                            uploadedFile.setError("Not loading as not relevant according to the import template, sheet name must contain " + sheetNameContains);
                            return uploadedFile;
                        }
                    }

                    if (templateParameters.get(SHEETNAMEMATCHES) != null && uploadedFile.getParameter(SHEETNAME) != null) {
                        String sheetNameMatches = templateParameters.get(SHEETNAMEMATCHES).toLowerCase().trim();

                        if (!uploadedFile.getParameter(SHEETNAME).toLowerCase().equals(sheetNameMatches)) {
                            uploadedFile.setError("Not loading as not relevant according to the import template, sheet name must be " + sheetNameMatches);
                            return uploadedFile;
                        }
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
                            // we assume the first lines are file headings and anything below needs to be joined together into an Azquo heading
                            // we now support headingsNoFileHeadingsWithInterimLookup in a non - version context
                            List<HeadingWithInterimLookup> headingsNoFileHeadingsWithInterimLookup = new ArrayList<>();
                            Map<List<String>, HeadingWithInterimLookup> headingsByLookupWithInterimLookup = new HashMap<>();
                            for (List<String> headings : standardHeadings) {
                                if (!headings.isEmpty()) {
                                    String fileHeading = null;
                                    StringBuilder azquoHeading = new StringBuilder();
                                    for (int i = 0; i < headings.size(); i++) {
                                        if (i == 0) {
                                            fileHeading = headings.get(i).toLowerCase(); // note tolower case - we want the file headings to be case insensitive
                                        } else {
                                            if (i > 1) {
                                                azquoHeading.append(";");
                                            }
                                            azquoHeading.append(headings.get(i));
                                        }
                                    }
                                    // there is no second value to the typed pair - that's only used when there's a version. The field says "WithInterimLookup" but there is no interim lookup where there's one sheet
                                    if (fileHeading.isEmpty()) {
                                        headingsNoFileHeadingsWithInterimLookup.add(new HeadingWithInterimLookup(azquoHeading.toString(), null));
                                    } else {
                                        headingsByLookupWithInterimLookup.put(Collections.singletonList(fileHeading), new HeadingWithInterimLookup(azquoHeading.toString(), null));
                                    }
                                }
                            }
                            uploadedFile.setHeadingsByFileHeadingsWithInterimLookup(headingsByLookupWithInterimLookup);
                            uploadedFile.setHeadingsNoFileHeadingsWithInterimLookup(headingsNoFileHeadingsWithInterimLookup);
                        }
                    } else {

                        // the thing here is to add the version headings as file headings looking up the Azquo headings from the Import Model
                        Map<List<String>, HeadingWithInterimLookup> headingsByFileHeadingsWithInterimLookup = new HashMap<>();
                        // and the headings without reference to the
                        List<HeadingWithInterimLookup> headingsNoFileHeadingsWithInterimLookup = new ArrayList<>();
                        /* There may be references without headings but not the other way around (as in we'd just ignore the headings)
                         * hence references needs to be the outer loop adding version headings where it can find them
                         * */
                        int index = 0;
                        for (List<String> headingReferenceColumn : headingReference) {
                            if (headingReferenceColumn.size() > 0 && !headingReferenceColumn.get(0).isEmpty()) {// we fill gaps when parsing the standard headings so there may be blanks in here, ignore them!
                                String reference = headingReferenceColumn.get(0); // the reference is the top one
                                List<String> azquoClauses = new ArrayList<>(); // in here will go the Azquo clauses looked up from the Import Model, the extra ones will now have to dealt with in a different way due to context - comments below
                                List<String> extraVersionClauses = new ArrayList<>();
                                // ok now we're going to look for this in the standard headings by the "top" heading
                                Iterator<List<String>> standardHeadingsIterator = standardHeadings.iterator();
                                // switch now we're allowing an import version to exist without a model. EFC to double check logic - todo
                                if (hasImportModel) {
                                    while (standardHeadingsIterator.hasNext()) {
                                        List<String> standardHeadingsColumn = standardHeadingsIterator.next();
                                        if (!standardHeadingsColumn.isEmpty() && standardHeadingsColumn.get(0).equalsIgnoreCase(reference)) {
                                            standardHeadingsIterator.remove(); // later when checking for required we don't want to add any again
                                            azquoClauses.addAll(standardHeadingsColumn.subList(1, standardHeadingsColumn.size()));
                                        }
                                    }

                                    if (azquoClauses.isEmpty()) {
                                        throw new Exception("On import version sheet " + importVersion + " no headings on Import Model found for " + reference + " - was this referenced twice?");
                                    } else if (headingReference.get(index).size() > 1) { // add the extra ones
                                        extraVersionClauses.addAll(headingReference.get(index).subList(1, headingReference.get(index).size()));
                                    }
                                } else {
                                    azquoClauses.addAll(headingReference.get(index));
                                }
                                StringBuilder azquoHeadingsAsString = new StringBuilder();
                                for (String clause : azquoClauses) {
                                    if (azquoHeadingsAsString.length() > 0 && !clause.startsWith(".")) {
                                        azquoHeadingsAsString.append(";");
                                    }
                                    azquoHeadingsAsString.append(clause);
                                }
                                // ok the extraVersionClauses were part of azquoClauses but there was a problem - what happens if the import model has context headings - a pipe then another heading?
                                // The extra clauses would only apply to the last context heading when they should apply to all or rather the first heading before pipe so then the others all inherit
                                if (azquoHeadingsAsString.indexOf("|") != -1) {
                                    String secondHalf = azquoHeadingsAsString.substring(azquoHeadingsAsString.indexOf("|"), azquoHeadingsAsString.length());
                                    azquoHeadingsAsString.setLength(azquoHeadingsAsString.indexOf("|"));
                                    // so, as mentioned in the previous comment, jam the extra clauses before the first pipe if it exists
                                    for (String clause : extraVersionClauses) {
                                        if (azquoHeadingsAsString.length() > 0 && !clause.startsWith(".")) {
                                            azquoHeadingsAsString.append(";");
                                        }
                                        azquoHeadingsAsString.append(clause);
                                    }
                                    azquoHeadingsAsString.append(secondHalf);
                                } else {
                                    for (String clause : extraVersionClauses) {
                                        if (azquoHeadingsAsString.length() > 0 && !clause.startsWith(".")) {
                                            azquoHeadingsAsString.append(";");
                                        }
                                        azquoHeadingsAsString.append(clause);
                                    }
                                }
                                // finally get the file headings if applicable
                                if (versionHeadings.size() > index && !versionHeadings.get(index).isEmpty()) {
                                    headingsByFileHeadingsWithInterimLookup.put(versionHeadings.get(index), new HeadingWithInterimLookup(azquoHeadingsAsString.toString(), reference));
                                } else {
                                    headingsNoFileHeadingsWithInterimLookup.add(new HeadingWithInterimLookup(azquoHeadingsAsString.toString(), reference));
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
                            if (required || (!standardHeadingsColumn.isEmpty() && topHeadings.containsValue("`" + standardHeadingsColumn.get(0) + "`"))) {
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
                                headingsNoFileHeadingsWithInterimLookup.add(new HeadingWithInterimLookup(azquoHeadingsAsString.toString(), standardHeadingsColumn.get(0)));
                            }
                        }
                        if (uploadedFile.getTemplateParameter("category lookups") != null) {
                            //add lookups for the categorisation statements
                            String[] catLookups = uploadedFile.getTemplateParameter("category lookups").split(";");
                            for (String catLookup : catLookups) {
                                String[] vals = catLookup.split("=");
                                String newHeading = vals[0].trim();
                                HeadingWithInterimLookup headingWithInterimLookup = new HeadingWithInterimLookup(newHeading + ";composition " + vals[1], newHeading);
                                headingsNoFileHeadingsWithInterimLookup.add(headingWithInterimLookup);

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
        //override is the parameter is sent by the user
        preProcessor = uploadedFile.getParameter(PREPROCESSOR);
        if (preProcessor == null && uploadedFile.getParameter(IMPORTVERSION)==null) {
            preProcessor = uploadedFile.getTemplateParameter(PREPROCESSOR);
        }
        if (preProcessor != null && !uploadedFile.getPath().endsWith("converted")) {
            if (!preProcessor.toLowerCase().endsWith(".groovy")) {
                if (!preProcessor.toLowerCase().endsWith(".xlsx")) {
                    preProcessor += ".xlsx";
                }
                ImportTemplate preProcess = ImportTemplateDAO.findForNameAndBusinessId(preProcessor, loggedInUser.getUser().getBusinessId());
                if (preProcess == null) {
                    throw new Error("Preprocessor: " + preProcessor + " not found");
                }
                try {
                    preProcessUsingPoi(loggedInUser, uploadedFile, SpreadsheetService.getHomeDir() + dbPath + loggedInUser.getBusinessDirectory() + importTemplatesDir + preProcess.getFilenameForDisk()); //
                    if (uploadedFile.getPath() == null) {
                        return uploadedFile;
                    }
                } catch (Exception e) {
                    uploadedFile.setError("Preprocessor error in " + uploadedFile.getTemplateParameter(PREPROCESSOR) + " : " + e.getMessage());
                    return uploadedFile;
                }
            } else {
                File file = new File(SpreadsheetService.getGroovyDir() + "/" + preProcessor);
                if (file.exists()) {
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
                        uploadedFile.setError("Preprocessor error in " + uploadedFile.getTemplateParameter(PREPROCESSOR) + " : " + e.getMessage());
                        if (pendingUploadConfig != null) {
                            pendingUploadConfig.incrementFileCounter();
                        }
                        return uploadedFile;
                    }
                    // todo - the import version switching will be broken!
                    if (importVersion != null && !importVersion.equalsIgnoreCase(uploadedFile.getParameter(IMPORTVERSION))) { // the template changed! Call this function again to load the new template
                        // there is a danger of a circular reference - protect against that?
                        // must clear template based parameters, new object
                        UploadedFile fileToProcessAgain = new UploadedFile(uploadedFile.getPath(), uploadedFile.getFileNames(), uploadedFile.getParameters(), true, uploadedFile.isValidationTest());
                        return readPreparedFile(loggedInUser, fileToProcessAgain, false, pendingUploadConfig, templateCache);
                    }
                } else {
                    uploadedFile.setError("unable to find preprocessor : " + uploadedFile.getTemplateParameter(PREPROCESSOR));
                    if (pendingUploadConfig != null) {
                        pendingUploadConfig.incrementFileCounter();
                    }
                    return uploadedFile;
                }
            }
        }

        DatabaseServer databaseServer = loggedInUser.getDatabaseServer();
        DatabaseAccessToken databaseAccessToken = loggedInUser.getDataAccessToken();
        // right - here we're going to have to move the file if the DB server is not local.
        if (!databaseServer.getIp().equals(LOCALIP)) {// the call via RMI is the same the question is whether the path refers to this machine or another
            // efc the final file name path wasn't being added, I don't know how it was lots, code *was* working. Somehow the change got lost, a pain! basically copy the tmp file name from the report server, should be fine
            uploadedFile.setPath(SFTPUtilities.copyFileToDatabaseServer(new FileInputStream(uploadedFile.getPath()), databaseServer.getSftpUrl() + (uploadedFile.getPath().substring(uploadedFile.getPath().lastIndexOf("/") + 1))));
        }
        if (uploadedFile.getTemplateParameter("debug") != null) {
            List<String> notice = new ArrayList<>();
            //find the name of the workbook...
            int nameCount = uploadedFile.getFileNames().size() - 2;
            if (nameCount < 0) nameCount = 0;
            String fileName = uploadedFile.getFileNames().get(nameCount);
            if (fileName.contains(".")) {
                fileName = fileName.substring(0, fileName.lastIndexOf("."));
            }
            fileName += ".tsv";

            loggedInUser.setLastFile(uploadedFile.getPath());
            loggedInUser.setLastFileName(fileName);
            // should probably not be HTML in here . . .
            notice.add("<a href=\"/api/Download?lastFile=true\">DOWNLOAD " + fileName + "</a>");
            uploadedFile.addToErrorHeadings(notice);
        }
        // used to be processed file, I see no advantage to that
        if (!loggedInUser.getUser().isAdministrator() && !loggedInUser.getUser().isDeveloper()){
            // then check privileges on uploading files
            if (uploadedFile.getParameter("importversion") == null ||
                    loggedInUser.getPendingUploadPermissions() == null ||
                    !loggedInUser.getPendingUploadPermissions().contains(uploadedFile.getParameter("importversion").toLowerCase())){
                throw new Exception("Insufficient priviledges to upload that type of file");
            }
        }

        uploadedFile = RMIClient.getServerInterface(databaseServer.getIp()).readPreparedFile(databaseAccessToken
                , uploadedFile
                , loggedInUser.getUser().getName());
        // run any executes defined in the file
        // the 2d arrays should probably be removed from post processor but not just yet
        List<List<List<String>>> systemData2DArrays = new ArrayList<>();
        if (uploadedFile.getTemplateParameter(POSTPROCESSOR) != null) {
            // set user choices to file params, could be useful to the execute
            for (String choice : uploadedFile.getParameters().keySet()) {
                System.out.println(choice + " : " + uploadedFile.getParameter(choice));
                SpreadsheetService.setUserChoice(loggedInUser, choice, uploadedFile.getParameter(choice));
            }
            loggedInUser.copyMode = uploadedFile.isValidationTest();
            try {
                uploadedFile.setPostProcessingResult(ReportExecutor.runExecute(loggedInUser, uploadedFile.getTemplateParameter(POSTPROCESSOR), systemData2DArrays, uploadedFile.getProvenanceId(), false).toString());
            } catch (Exception e) {
                loggedInUser.copyMode = false;
                throw e;
            }
        }
// perhaps redundant? Seems just vanilla execute is being used for validation
        if (uploadedFile.getTemplateParameter(VALIDATION) != null && uploadedFile.isValidationTest()) {
            // set user choices to file params, could be useful to the execute
            for (String choice : uploadedFile.getParameters().keySet()) {
                System.out.println(choice + " : " + uploadedFile.getParameter(choice));
                SpreadsheetService.setUserChoice(loggedInUser, choice, uploadedFile.getParameter(choice));
            }
            loggedInUser.copyMode = true;
            try {
                ReportExecutor.runExecute(loggedInUser, uploadedFile.getTemplateParameter(VALIDATION), systemData2DArrays, uploadedFile.getProvenanceId(), false);
            } catch (Exception e) {
                loggedInUser.copyMode = false;
                throw e;
            }
        }
        loggedInUser.copyMode = false;
        if (!systemData2DArrays.isEmpty()) {
            // then we had some validation results, need to identify lines in the file with problems
            // ok leaving this for the mo. I have all the data I need it's just a question of the structure to store it, as we have rejected lines in Uploaded file so we'll have warning lines and possibly
            // error descriptions. Leaving the warning lines as a map as they may not be added in order, will sort the lines and repackage after
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
                    uploadedFile.addToErrorHeadings(errorHeadings);
                    if (!errorLines.isEmpty()) { // then I need to find them. First thing to do is find the relevant column
                        // going to try based on feedback in the processed file
                        if (uploadedFile.getFileHeadings() != null) { // if we don't have the original set of headings we won't be able to check the file
                            int index = 0;
                            if ("lineno".equalsIgnoreCase(keyColumn)) {
                                index = -1; // special case - the value IS the line number
                                for (String errorKey : new HashSet<>(errorLines.keySet())) {
                                    // worth an explanation, line no can hit other files so if it has a - in it then take the file name from before the -
                                    // and then check the uploadedFile file name contains it otherwise ditch the error line
                                    if (errorKey.contains("-")) {
                                        Map<String, String> errorLine = errorLines.remove(errorKey);
                                        if (uploadedFile.getFileName().toLowerCase().contains(errorKey.toLowerCase().substring(0, errorKey.indexOf("-")))) {
                                            errorLines.put(errorKey.substring(errorKey.indexOf("-") + 1), errorLine);
                                        }
                                    }
                                }
                            } else {
                                for (List<String> fileHeading : uploadedFile.getFileHeadings()) {
//                            for (String subHeading : fileHeading) { // currently looking up against the model reference not the file reference
                                    HeadingWithInterimLookup headingWithInterimLookup = uploadedFile.getHeadingsByFileHeadingsWithInterimLookup().get(fileHeading);
                                    // so the current criteria - it's a heading which looked up something on the model and it's the lookup which is the name we're interested in. Import Model having the canonical name for their purposes
                                    if (headingWithInterimLookup != null && headingWithInterimLookup.getInterimLookup() != null && headingWithInterimLookup.getInterimLookup().equalsIgnoreCase(keyColumn.trim())) {
                                        // we have a winner
                                        break;
                                    }
                                    index++;
                                }
                                // need a new criteria for the import wizard - try to match the heading straight, hopefully will not interfere with existing matching
                                if (index == uploadedFile.getFileHeadings().size()) { // nothing found, try a straight match
                                    index = 0;
                                    for (List<String> fileHeading : uploadedFile.getFileHeadings()) {
                                        if (!fileHeading.isEmpty() && fileHeading.get(0).equalsIgnoreCase(keyColumn.trim())) {
                                            break;
                                        }
                                        index++;
                                    }
                                }
                            }
                            if (index < uploadedFile.getFileHeadings().size()) { // then we found the relevant column
                                // I'm going to go to the server code to look this up
                                Map<Integer, LineIdentifierLineValue> linesWithValuesInColumn = RMIClient.getServerInterface(databaseServer.getIp()).getLinesWithValuesInColumn(uploadedFile, index, new HashSet<>(errorLines.keySet()));// new hash set to be serializable . . .
                                // ok so we should now finally have the information we need across the two maps (linesWithValuesInColumn and errorLines)
                                // What needs to go into a table is the line number, the line itself and error(s) for that line, there could be multiple errors for the line
                                for (Integer key : linesWithValuesInColumn.keySet()) {
                                    warningLineMap.computeIfAbsent(key, t -> new UploadedFile.WarningLine(key, keyColumn + ":" + linesWithValuesInColumn.get(key).getLineIdentifier(), linesWithValuesInColumn.get(key).getLineValue())).addErrors(errorLines.get(linesWithValuesInColumn.get(key).getLineIdentifier().toLowerCase()));
                                }
                            } else {
                                throw new Exception("unable to find " + keyColumn + " from validation in uploaded file");
                            }
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
        if (pendingUploadConfig != null) {
            pendingUploadConfig.incrementFileCounter();
        }
        return uploadedFile;
    }

    // in this context the custom headings range is for when there are multiple versions of an importer based off a master sheet -
    // e.g. the big Ed Broking import sheet for Risk which will have sub sheets - these sheets will have a named range,

    private static List<List<String>> sheetInfo(ImportTemplateData importTemplateData, String toTest) {
        for (String sheetName : importTemplateData.getSheets().keySet()) {
            if (sheetName.equalsIgnoreCase(toTest)) {
                return importTemplateData.getSheets().get(sheetName);
            }
        }
        return null;
    }

    private static String findRelevantTemplate(LoggedInUser loggedInUser, UploadedFile uploadedFile, ImportTemplateData importTemplateData, String currentTemplateName){
        for (String sheet:importTemplateData.getSheets().keySet()) {
            Map<String,String>templateParameters = getTemplateParameters(importTemplateData.getSheets().get(sheet));
            String regexAsString = templateParameters.get(ImportWizard.IMPORTFILENAMEREGEX);
            if (regexAsString != null) {
                Pattern p = Pattern.compile(regexAsString);
                Matcher m = p.matcher(uploadedFile.getFileName().toLowerCase(Locale.ROOT));
                if (m.find()) {
                    setupImportAndDataNames(loggedInUser, sheet, uploadedFile, importTemplateData.getSheets().get(sheet));
                    return sheet;
                }
            }
        }


        return currentTemplateName;

    }

    private static void setupImportAndDataNames(LoggedInUser loggedInUser, String templateName, UploadedFile uploadedFile, List<List<String>>templateSheetData){
        //the report wizard needs a structure of data values, and an import structure is created to be able to show reports of the latest data uploaded
        for (int col=0;col < templateSheetData.get(0).size();col++){
            for (int row=1;row<templateSheetData.size();row++){
                if (col< templateSheetData.get(row).size()){
                    String cell = templateSheetData.get(row).get(col);
                    if (cell==null || cell.length()==0) {
                        break;
                    }
                    if (cell.toLowerCase(Locale.ROOT).startsWith("peers")) {
                        CommonReportUtils.getDropdownListForQuery(loggedInUser, "edit:create Data" + StringLiterals.MEMBEROF + templateName + " Values" + StringLiterals.MEMBEROF + templateSheetData.get(1).get(col));
                        break;
                    }

                }else{
                    break;
                }
            }
        }
        CommonReportUtils.getDropdownListForQuery(loggedInUser,"edit:create Imports" + StringLiterals.MEMBEROF +  templateName + " Imports->Import from " + uploadedFile.getFileName());

    }

    private static boolean nameCompare(String target, String source) {
        if (source.startsWith("*")) {
            if (target.toLowerCase().contains(source.toLowerCase().substring(1))) {
                return true;
            }
        } else {
            if (target.toLowerCase().startsWith(source.toLowerCase())) {
                return true;
            }
        }
        return false;

    }

    private static Map<String,String>getTemplateParameters(List<List<String>>templateData){
        Map<String,String> toReturn = new HashMap<>();
        if (templateData == null){
            return toReturn;
        }
        boolean hasParameters = false;
        for (List<String> row:templateData){
            if (hasParameters){
                if (row.get(0)==null || row.get(0).length()==0 || row.size() < 2){
                    return toReturn;
                }
                toReturn.put(row.get(0).toLowerCase(Locale.ROOT), row.get(1));
            }
            if (row!=null && row.size() > 0 && row.get(0).equalsIgnoreCase("parameters")){
                hasParameters  = true;
            }
        }
        return toReturn;
    }

    enum ImportSheetScanMode {OFF, TOPHEADINGS, CUSTOMHEADINGS, STANDARDHEADINGS, PARAMETERS}

    public static void importSheetScan(List<List<String>> sheet
            , Map<RowColumn, String> topHeadings
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
                        if (!topHeadings.containsValue(cellValue)) { // not allowing multiple identical top headings. Typically can happen from a merge spreading across cells
                            topHeadings.put(new RowColumn(rowIndex, cellIndex), cellValue);
                        }
                    } else if (mode == ImportSheetScanMode.CUSTOMHEADINGS) { // custom headings to be used for lookup on files - I'll watch for limiting by column though it hasn't been used yet
                        if (customHeadingsRange.getFirstCell().getCol() == -1 || (cellIndex >= customHeadingsRange.getFirstCell().getCol() && cellIndex <= customHeadingsRange.getLastCell().getCol())) {
                            while (customHeadings.size() <= (cellIndex + 1)) { // make sure there are enough lists to represent the heading columns were adding to
                                customHeadings.add(new ArrayList<>());
                            }
                            customHeadings.get(cellIndex).add(cellValue.toLowerCase().replace("\n", " ").replace("  ", " ")); // NOTE! We want the looking up fo file headings to be case insensetive, hence the lower case here, also zap carriage returns, they can sneak in and out
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
                            firstCellValue = null;
                        }

                    } else { // after the first cell when not headings
                        // yes extra cells will ovrride subsequent key pair values. Since this would be an incorrect sheet I'm not currently bothered by this
                        if (mode == ImportSheetScanMode.PARAMETERS && firstCellValue!=null &&  cellIndex==1) {
                            // gathering parameters
                           templateParameters.put(firstCellValue.toLowerCase(), cellValue);
                        }
                    }
                }
            }

            if (blankLine && mode != ImportSheetScanMode.TOPHEADINGS && mode != ImportSheetScanMode.CUSTOMHEADINGS) { // top headings will tolerate blank lines, customheadings also require them based off oilfields, other modes won't so switch off
                mode = ImportSheetScanMode.OFF;
            }
        }
    }

    // we're going to allow multiple import models selected by a parameter, hence need the ability to get just parameters early before parsing starts in earnest
    // maybe factor with above, have a think
    public static Map<String, String> scanJustParameters(List<List<String>> sheet) {
        Map<String, String> templateParameters = new HashMap<>();
        ImportSheetScanMode mode = ImportSheetScanMode.OFF;
        for (List<String> row : sheet) {
            // unfortunately while it seems POI might skip blank lines it seems it might also have blank cell values (this may not be a fault of POI, perhaps an Excel quirk)
            // regardless I need to check for a line only having blank cells and adjusting modes accordingly
            boolean blankLine = true;
            String firstCellValue = null;
            for (String cellValue : row) {
                if (!cellValue.isEmpty()) {
                    blankLine = false;
                    if (firstCellValue == null) {
                        firstCellValue = cellValue;
                        if ("PARAMETERS".equalsIgnoreCase(firstCellValue)) { // string literal move?
                            mode = ImportSheetScanMode.PARAMETERS;
                        }
                    } else { // after the first cell when not headings
                        // yes extra cells will override subsequent key pair values. Since this would be an incorrect sheet I'm not currently bothered by this
                        if (mode == ImportSheetScanMode.PARAMETERS) { // gathering parameters
                            templateParameters.put(firstCellValue.toLowerCase(), cellValue);
                        }
                    }
                }
            }
            if (blankLine && mode == ImportSheetScanMode.PARAMETERS) {
                return templateParameters;
            }
        }
        return templateParameters;
    }


    /* for parsing parameters out of a file name. Cumulative if files in zips or sheets in workbooks
     have parameters as well as their "parent" file */
    public static void addFileNameParametersToMap(String fileName, Map<String, String> map) {
        try {
            int bracketPos = 0;
            while (bracketPos >= 0) {
                bracketPos = fileName.indexOf("(", bracketPos + 1);
                if (bracketPos > 0) {
                    int startBrackets = bracketPos;
                    int endBrackets = fileName.indexOf(")", bracketPos);
                    //there may be parameter names which include backets....
                    int internalBrackets = bracketPos;
                    internalBrackets = fileName.indexOf("(", internalBrackets + 1);
                    while (endBrackets > 0 && internalBrackets > 0 && internalBrackets < endBrackets) {
                        endBrackets = fileName.indexOf(")", endBrackets + 1);
                        internalBrackets = fileName.indexOf("(", internalBrackets + 1);
                    }
                    if (endBrackets > 0) {
                        String parseString = fileName.substring(startBrackets + 1, endBrackets);
                        bracketPos = endBrackets;
                        if (parseString.contains("=")) {
                            StringTokenizer stringTokenizer = new StringTokenizer(parseString, ";");
                            while (stringTokenizer.hasMoreTokens()) {
                                String pair = stringTokenizer.nextToken().trim();
                                if (pair.contains("=")) {
                                    String left = pair.substring(0, pair.indexOf("=")).trim();
                                    String right = pair.substring(pair.indexOf("=") + 1).trim();
                                    map.put(left.toLowerCase(), right);
                                }else{
                                    if (pair.toLowerCase(Locale.ROOT).equals("usdates")){
                                        map.put("usdates","true");
                                    }
                                }
                            }
                        }
                    } else {
                        bracketPos = fileName.length();
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("Can't parse file name parameters correctly");
            e.printStackTrace();
        }
    }

    static SimpleDateFormat YYYYMMDD = new SimpleDateFormat("yyyy-MM-dd");

    private static DataFormatter df = new DataFormatter();

    // POI Version, used when converting sheets to csv. Essentially get a value of the cell as either an unformatted number or as a string similar to how it
    // is rendered in Excel, Some hacking to standardise date formats and remove escape characters
    // POI 4.0 version EFC pasted, I really don't like doing this but it's required at the moment
    private static org.apache.poi.ss.usermodel.DataFormatter df2 = new org.apache.poi.ss.usermodel.DataFormatter();

    public static String getACellValue(Sheet sheet, AreaReference areaRef) {
        return getCellValue(sheet,areaRef.getFirstCell().getRow(),areaRef.getFirstCell().getCol());
    }

    public static String getCellValue(Sheet sheet, int row, int col) {
        try{
            Cell cell = sheet.getRow(row).getCell(col);
            try{
                return cell.getStringCellValue();
            }catch(Exception e){

                if (cell.getCellStyle().getDataFormatString().contains("mm")) {
                    Long l =  ((long)cell.getNumericCellValue()-25569) * 86400000;
                    LocalDateTime dt = LocalDateTime.ofInstant(Instant.ofEpochMilli(l),
                            TimeZone.getDefault().toZoneId());
                    return DateTimeFormatter.ofPattern("yyyy-MM-dd").format(dt);
                }else{
                    String st = cell.getNumericCellValue()+ "";
                    if (st.endsWith(".0")) {
                        return st.substring(0, st.length() - 2);
                    }
                    return st;
                }
            }
        }catch(Exception e){
            return "";
        }
     }

    public static String getkCellValue(org.apache.poi.ss.usermodel.Cell cell) {
        return getCellValueUS(cell, false);
    }

    public static String getCellValueUS(org.apache.poi.ss.usermodel.Cell cell, boolean usDates){
        String returnString = "";
        if (cell == null) {
            return "";
        }
        //if (colCount++ > 0) bw.write('\t');
        if (cell.getCellType() == STRING || (cell.getCellType() == FORMULA && cell.getCachedFormulaResultType() == STRING)) {
            try {
                returnString = cell.getStringCellValue().replace(Character.toString((char) 160), "");// I assume means formatted text? The 160 is some kind of hard space that causes trouble and is unaffected by trim(), zap it
                 if (usDates){
                     LocalDate d = DateUtils.isUSDate(returnString);
                     if (d!=null){
                          returnString = d.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                     }
                 }
            } catch (Exception ignored) {
            }
        } else if (cell.getCellType() == NUMERIC || (cell.getCellType() == FORMULA && cell.getCachedFormulaResultType() == NUMERIC)) {
            // first we try to get it without locale - better match on built in formats it seems
            String dataFormat = BuiltinFormats.getBuiltinFormat(cell.getCellStyle().getDataFormat());
            if (dataFormat == null) {
                dataFormat = cell.getCellStyle().getDataFormatString();
            }
            if (dataFormat == null) {
                dataFormat = ""; // stop npes, can seem to happen with xls files
            }
            Double returnNumber = cell.getNumericCellValue();
            returnString = returnNumber.toString();
            if (returnString.contains("E")) {
                returnString = String.format("%f", returnNumber);
            }
            if (returnNumber % 1 == 0) {
                // specific condition - integer and format all 000, then actually use the format. For zip codes

                if (dataFormat.length() > 1 && dataFormat.contains("0") && dataFormat.replace("0", "").isEmpty()) {
                    // easylife tripped up this "zipcode" conditional by having a formula in there, requires a formula evaluator be passed
                    if (returnNumber == 0) {
                        return "";
                    }
                    if (cell.getCellType() == FORMULA) {
                        returnString = df2.formatCellValue(cell, cell.getSheet().getWorkbook().getCreationHelper().createFormulaEvaluator()); // performance issues on the formula evaluator??
                    } else {
                        returnString = df2.formatCellValue(cell);
                    }
                } else {
                    returnString = returnNumber.longValue() + "";
                }
            }
            if (dataFormat.equals("h:mm") && returnString.length() == 4) {
                //ZK BUG - reads "hh:mm" as "h:mm" - this may be a moot point now we're POI
                returnString = "0" + returnString;
            } else {
                if (dataFormat.toLowerCase().contains("m") || dataFormat.toLowerCase().contains("y")) {
                    if (returnNumber == 0) {
                        return "";
                    }
                    if ((dataFormat.indexOf("/") > 0 && dataFormat.indexOf("/", dataFormat.indexOf("/") + 1) > 0)
                            || (dataFormat.indexOf("-") > 0 && dataFormat.indexOf("-", dataFormat.indexOf("-") + 1) > 0)
                            || dataFormat.length() > 6) { // two dashes or two slashes or greater than 6. Used to be just greater than 6, now poi says things like d/m/yy so we need to be a bit more clever
                        try {
                            returnString = YYYYMMDD.format(cell.getDateCellValue());
                        } catch (Exception e) {
                            //not sure what to do herce.
                        }
                    } else { // it's still a date - match the defauilt format
                        // this seems to be required as if the date is based off another cell then the normal formatter will return the formula
                        CellDateFormatter cdf = new CellDateFormatter(Locale.UK, dataFormat);
                        returnString = cdf.format(cell.getDateCellValue());
                    }
                }
            }
        } else if (cell.getCellType() == BOOLEAN || (cell.getCellType() == FORMULA && cell.getCachedFormulaResultType() == BOOLEAN)) {
            returnString = cell.getBooleanCellValue() + "";
        } else if (cell.getCellType() != BLANK) {
            if (cell.getCellType() == FORMULA) {
                //System.out.println("other formula cell type : " + cell.getCachedFormulaResultType());
            }
            //System.out.println("other cell type : " + cell.getCellType());
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

        /*   Merged cells in headings may cause problems, but ignore for the moment....... (can use 'up and left' rule if necessary)

        // Deal with merged cells (IN HEADINGS ONLY - work for Arcana), not sure if there's a performance issue here? If a heading spans successive cells need to have the span value
        if (returnString.isEmpty() && cell.getSheet().getNumMergedRegions() > 0) {
            int rowIndex = cell.getRowIndex();
            int cellIndex = cell.getColumnIndex();
            for (int i = 0; i < cell.getSheet().getNumMergedRegions(); i++) {
                org.apache.poi.ss.util.CellRangeAddress region = cell.getSheet().getMergedRegion(i); //Region of merged cells
                //check first cell of the region
                //LOGIC CHANGE AGAIN! get all cells except the top left.
                if (rowIndex >= region.getFirstRow()
                        && rowIndex <= region.getLastRow()
                        && cellIndex >= region.getFirstColumn()
                        && cellIndex <= region.getLastColumn()
                        && (rowIndex > region.getFirstRow() || cellIndex > region.getFirstColumn())
                    ) {
                    returnString = getCellValue(cell.getSheet(),region.getFirstRow(),region.getFirstColumn());
                }
            }
        }
        */
        return returnString.trim();
    }

    // ZK version of the above - still used by the "download a report, edit and upload it" functionality. Hopefully removed later.

    public static DoubleAndOrString getCellValue(io.keikai.api.model.Sheet sheet, int r, int c) {
        Double returnNumber = null;
        String returnString = null;
        Range range = Ranges.range(sheet, r, c);
        CellData cellData = range.getCellData();
        String dataFormat = sheet.getInternalSheet().getCell(r, c).getCellStyle().getDataFormat();
        //if (colCount++ > 0) bw.write('\t');
        if (cellData != null) {
            String stringValue = "";
            // EFC adjusted logic on
            if (!cellData.isBlank() && !(cellData.isFormula() && cellData.getResultType() == CellData.CellType.BLANK)) {
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
            }
            returnString = stringValue;
        }
        assert returnString != null;
        if (returnString.startsWith("`") && returnString.indexOf("'", 1) < 0) {
            returnString = returnString.substring(1);
        }
        if (returnString.startsWith("'") && returnString.indexOf("'", 1) < 0)
            returnString = returnString.substring(1);//in Excel some cells are preceded by a ' to indicate that they should be handled as strings
        return new DoubleAndOrString(returnNumber, returnString.trim());
    }

    // similar to uploading a report


    public static UploadedFile uploadImportTemplate(UploadedFile uploadedFile, LoggedInUser loggedInUser, String userComment) throws
            IOException {
        //rule put in by WFC Jan-21
        String templateName = uploadedFile.getFileName();
        String fileName = templateName;
        String suffix = templateName.substring(templateName.length()-4);
        int fileNames = uploadedFile.getFileNames().size();
        if (!templateName.contains(".")&& fileNames>1) {
            fileName = uploadedFile.getFileNames().get(fileNames-2);
        }
        boolean assignToLoggedInUserDB = fileName.toLowerCase().contains("import templates");
        long time = System.currentTimeMillis();
        uploadedFile.setDataModified(true); // ok so it's not technically data modified but the file has been processed correctly.
        int businessId = loggedInUser.getUser().getBusinessId();
        String pathName = loggedInUser.getBusinessDirectory();
         int importPos =templateName.toLowerCase(Locale.ROOT).indexOf(StringLiterals.IMPORTDATA + "=");
        if (importPos > 0){
            int closeBracketsPos = templateName.indexOf(")",importPos);
            if (closeBracketsPos > 0){
                templateName = templateName.substring(importPos+11,closeBracketsPos).toLowerCase(Locale.ROOT).trim();
            }
        }
        ImportTemplate importTemplate = ImportTemplateDAO.findForNameAndBusinessId(templateName, loggedInUser.getUser().getBusinessId());
        if (userComment==null){
            userComment = "";
        }
        if (importTemplate != null) {
            // zap the old one first
            try {
                Files.deleteIfExists(Paths.get(SpreadsheetService.getHomeDir() + dbPath + pathName + importTemplatesDir + importTemplate.getFilenameForDisk()));
            } catch (Exception e) {
                System.out.println("problem deleting old template");
                e.printStackTrace();
            }
             importTemplate.setFilename(fileName); // it might have changed, I don't think much else under these circumstances
            importTemplate.setDateCreated(LocalDateTime.now());
            importTemplate.setNotes(userComment);
        } else {
            importTemplate = new ImportTemplate(0, LocalDateTime.now(), businessId, loggedInUser.getUser().getId(), templateName, fileName,  userComment); // default to ZK now
        }
        ImportTemplateDAO.store(importTemplate);
        // right here is a problem - what about other users currently logged in with that database? todo
        if (suffix.equals(".png")|| suffix.equals(".jpg")){
            //business logo
            loggedInUser.getBusiness().setLogo("templates-" + importTemplate.getId());
            if (userComment!=null && userComment.length()==7){
                loggedInUser.getBusiness().setBannerColor(userComment);
            }
             BusinessDAO.store(loggedInUser.getBusiness());
        }

        if (assignToLoggedInUserDB) {
            Database database = loggedInUser.getDatabase();
            database.setImportTemplateId(importTemplate.getId());
            DatabaseDAO.store(database);
        }
        Path fullPath = Paths.get(SpreadsheetService.getHomeDir() + dbPath + pathName + importTemplatesDir + importTemplate.getFilenameForDisk());
        Files.createDirectories(fullPath.getParent()); // in case it doesn't exist
        Files.copy(Paths.get(uploadedFile.getPath()), fullPath); // and copy
        // ---- end report uploading
        uploadedFile.setImportTemplate(true);
        OPCPackage opcPackage = null;
        Workbook ppBook = null;
        if (suffix.equals("xlsx")) {
            //templates may also contain some sheets for direct upload, identified by ' upload' at the end of the sheet name
            try (FileInputStream fi = new FileInputStream(uploadedFile.getPath())) { // this will hopefully guarantee that the file handler is released under windows
                opcPackage = OPCPackage.open(fi);
            } catch (Exception e) {
                e.printStackTrace();
                throw new IOException("Cannot load preprocessor template");
            }
            ppBook = new XSSFWorkbook(opcPackage);
            for (int sheetNo = 0; sheetNo < ppBook.getNumberOfSheets(); sheetNo++) {
                org.apache.poi.ss.usermodel.Sheet sheet = ppBook.getSheetAt(sheetNo);
                if (sheet.getSheetName().toLowerCase().endsWith(" upload")) {
                    //need to clear all templates before loading internal sheet...
                    Map<String, String> holdParameters = uploadedFile.getParameters();
                    uploadedFile.getParameters().remove("preprocessor");
                    uploadedFile.getParameters().remove("importversion");
                    readSheet(loggedInUser, uploadedFile, sheet, sheet.getSheetName(),null, null, userComment);
                    uploadedFile.setParameters(holdParameters);
                }
            }
            opcPackage.revert();
        }

        uploadedFile.setProcessingDuration(System.currentTimeMillis() - time);
        return uploadedFile;
    }


    public static boolean hasImportTemplate(LoggedInUser loggedInUser, UploadedFile uploadedFile){
        try {
            HashMap<String, ImportTemplateData> templateCache = new HashMap<>();
            if (getImportTemplateForUploadedFile(loggedInUser, uploadedFile, templateCache) != null) {
                for (String template:templateCache.keySet()){
                    ImportTemplateData templateData = templateCache.get(template);
                    for (String sheet:templateData.getSheets().keySet()){
                        for (String fileName:uploadedFile.getFileNames()){
                            if (fileName.toLowerCase(Locale.ROOT).startsWith((sheet.toLowerCase(Locale.ROOT)))){
                                return true;
                            }
                        }
                    }
                }
            }
        }catch(Exception e){

        }
        return false;
    }

    public static ImportTemplateData getImportTemplateForUploadedFile(LoggedInUser loggedInUser, UploadedFile uploadedFile, HashMap<String, ImportTemplateData> templateCache) throws Exception {
        String importTemplateName = uploadedFile != null ? uploadedFile.getParameter(IMPORTTEMPLATE) : null;
        if (importTemplateName != null) { // priority to a manually specified import name
            if (importTemplateName.endsWith(".xlsx")) importTemplateName = importTemplateName.replace(".xlsx", "");
            String finalImportTemplateName = importTemplateName;
            if (templateCache != null) {
                return templateCache.computeIfAbsent(importTemplateName, t -> {
                            try {
                                ImportTemplate importTemplate = ImportTemplateDAO.findForNameBeginningAndBusinessId(finalImportTemplateName, loggedInUser.getUser().getBusinessId());
                                return getImportTemplateData(importTemplate, loggedInUser);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                            return null;
                        }
                );
            } else {
                ImportTemplate importTemplate = ImportTemplateDAO.findForNameBeginningAndBusinessId(importTemplateName, loggedInUser.getUser().getBusinessId());
                return getImportTemplateData(importTemplate, loggedInUser);
            }
        }
        // if no import template specified see if there is a database one - todo - could probably factor with above a little
        if (loggedInUser.getDatabase().getImportTemplateId() != -1) {
            if (templateCache != null) {
                return templateCache.computeIfAbsent(loggedInUser.getDatabase().getImportTemplateId() + "", t -> {
                            try {
                                ImportTemplate importTemplate = ImportTemplateDAO.findById(loggedInUser.getDatabase().getImportTemplateId());
                                return getImportTemplateData(importTemplate, loggedInUser);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                            return null;
                        }
                );
            } else {
                ImportTemplate importTemplate = ImportTemplateDAO.findById(loggedInUser.getDatabase().getImportTemplateId());
                return getImportTemplateData(importTemplate, loggedInUser);
            }
        }
        return null;
    }


    public static ImportTemplateData getImportTemplateData(ImportTemplate importTemplate, LoggedInUser loggedInUser) throws Exception {
        if (importTemplate != null) {
            // chunks of this will be factored off later when I want faster data file conversion
            ImportTemplateData importTemplateData = new ImportTemplateData();
            // I'm not supporting xls templates
            System.out.println("template path : " + SpreadsheetService.getHomeDir() + dbPath + loggedInUser.getBusinessDirectory() + importTemplatesDir + importTemplate.getFilenameForDisk());
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
            // due to windows paranoia, keep an eye on this. Might cause files to be write locked
            opcPackage.revert();
            return importTemplateData;
        }
        return null;
    }

    /*
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
            // worried about write locking the file in windows . . .
            opcPackage.revert();

        }
    */
    private static void processSheet(
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

    public static class MergedCellsHandler extends DefaultHandler {
        private final ArrayList<String> merges;

        MergedCellsHandler(ArrayList<String> merges) {
            this.merges = merges;
        }

        public void startElement(String uri, String localName, String name, Attributes attributes) {
            if ("mergeCell".equals(name)) {
                merges.add(attributes.getValue("ref"));
            }
        }
    }


    // maybe redo at some point checking variable names etc but this is fine enough for the moment todo - further changes since then, EFC check
    public static void preProcessUsingPoi(LoggedInUser loggedInUser, UploadedFile uploadedFile, String preprocessor) throws Exception {
        OPCPackage opcPackage = null;
        Workbook ppBook = null;

        try {
            // WARNING!!! EFC note - this cacheing of workbooks causes problems. I'm commenting, if uncommented without investigation PROBLEMS WILL RECUR
            //if (loggedInUser.getPreprocessorName()==null || !loggedInUser.getPreprocessorName().equals(preprocessor)) {
                 setUpPreprocessor(loggedInUser, uploadedFile, preprocessor);
            //}

            ppBook = loggedInUser.getPreprocessorLoaded();
            if (ppBook==null){//json
                return;
            }
            List<String> ignoreSheetList = getList(ppBook, "az_IgnoreSheets");
            if (ignoreSheetList.size() > 0) {
                 for (String ignoreSheet : ignoreSheetList) {
                    if (ignoreSheet.length() > 0) {
                        for (String fName : uploadedFile.getFileNames()) {
                            if (nameApplies(ignoreSheet.toLowerCase(Locale.ROOT), fName.toLowerCase(Locale.ROOT))){
                                uploadedFile.setPath(null);
                                return;
                            }
                        }
                    }
                }
            }
            List<String> useSheetList = getList(ppBook,"az_UseSheets");
            if (useSheetList.size() > 0) {
                boolean hasUseSheets = false;
                String sheetName = uploadedFile.getFileNames().get(uploadedFile.getFileNames().size() - 1).toLowerCase(Locale.ROOT);
                for (String useSheet : useSheetList) {
                    boolean use = false;
                    if (useSheet.length() > 0) {
                        hasUseSheets = true;
                        if (sheetName.contains(useSheet)) {
                            use = true;
                            break;
                        }
                        //using 'contains' - maybe should check wildcards
                    }
                    if (hasUseSheets && !use) {
                        uploadedFile.setPath(null);
                        return;
                    }
                }
            }

            String filePath = uploadedFile.getPath();
            Map<String, String> headingsLookups = setupHeadingsMappings(ppBook);

            org.apache.poi.ss.usermodel.Name inputLineRegion = BookUtils.getName(ppBook, "az_input");
            AreaReference inputAreaRef = new AreaReference(inputLineRegion.getRefersToFormula(), null);
            int inputRowNo = inputAreaRef.getLastCell().getRow();
            org.apache.poi.ss.usermodel.Name outputLineRegion = BookUtils.getName(ppBook, "az_output");
            AreaReference outputAreaRef = new AreaReference(outputLineRegion.getRefersToFormula(), null);
            if (outputAreaRef.getFirstCell().getCol()==-1){
                int lastOutputCell = ppBook.getSheetAt(0).getRow(outputAreaRef.getFirstCell().getRow()).getLastCellNum();
                outputLineRegion.setRefersToFormula("'" + ppBook.getSheetAt(0).getSheetName() + "'!" + BookUtils.rangeToText(outputAreaRef.getFirstCell().getRow(), 0) + ":" + BookUtils.rangeToText(outputAreaRef.getLastCell().getRow(), lastOutputCell));
            }
            org.apache.poi.ss.usermodel.Name ignoreRegion = BookUtils.getName(ppBook, "az_ignore");
            org.apache.poi.ss.usermodel.Name fileNameRegion = BookUtils.getName(ppBook, "az_filename");
            AreaReference fileNameAreaRef = null;
            String fileName = null;
            if (fileNameRegion != null) {
                fileNameAreaRef = new AreaReference(fileNameRegion.getRefersToFormula(), null);
                List<String> fileNames = uploadedFile.getFileNames();
                for (int i = fileNames.size() - 1; i >= 0; i--) {
                    //do not use the sheet name.
                    fileName = fileNames.get(i);
                    if (fileName.contains(".")) {
                        break;
                    }
                }
            }
            AreaReference ignoreRef = null;
            if (ignoreRegion != null) {
                ignoreRef = new AreaReference(ignoreRegion.getRefersToFormula(), null);
            }
            org.apache.poi.ss.usermodel.Name optionsRegion = BookUtils.getName(ppBook, "az_options");
            String options = null;
            boolean backwards = false;
            Sheet inputSheet = ppBook.getSheet(inputLineRegion.getSheetName());
            if (optionsRegion != null) {
                options = getACellValue(inputSheet, new AreaReference(optionsRegion.getRefersToFormula(), null));
                if (options.toLowerCase().contains("backward")) {
                    backwards = true;
                }
            }
            org.apache.poi.ss.usermodel.Name sheetNameRegion = BookUtils.getName(ppBook, "az_sheetname");
            if (sheetNameRegion != null) {
                AreaReference sheetNameAreaRef = new AreaReference(sheetNameRegion.getRefersToFormula(), null);
                String sheetName = uploadedFile.getFileNames().get(uploadedFile.getFileNames().size() - 1);
                setCellValue(inputSheet, sheetNameAreaRef.getFirstCell().getRow(), sheetNameAreaRef.getFirstCell().getCol(), sheetName);
            }


            Map<Name, AreaReference> persistNames = getPersistNames(ppBook);
            //Sheet inputSheet = ppBook.getSheet(inputLineRegion.getSheetName());
            Sheet outputSheet = ppBook.getSheet(outputLineRegion.getSheetName());
            int headingStartRow = inputAreaRef.getFirstCell().getRow();
             int existingHeadingRows = inputAreaRef.getLastCell().getRow() - headingStartRow;
            int outputRow = outputAreaRef.getFirstCell().getRow();
            String outFile = filePath + " converted";
            File writeFile = new File(outFile);
            writeFile.delete(); // to avoid confusion
            BufferedWriter fileWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outFile), StandardCharsets.UTF_8));
            CsvMapper csvMapper = new CsvMapper();
            csvMapper.enable(CsvParser.Feature.WRAP_AS_ARRAY);
            String fileEncoding = uploadedFile.getParameter(FILEENCODING);
            char delimiter = ',';
//        System.out.println("get lines with values and column, col index : " + columnIndex);
//        System.out.println("get lines with values and column, values to check : " + valuesToCheck);
            if (uploadedFile.isConvertedFromWorksheet()) {
                delimiter = '\t';
            } else {
                try (BufferedReader br = Files.newBufferedReader(Paths.get(uploadedFile.getPath()), StandardCharsets.UTF_8)) {
                    // grab the first line to check on delimiters
                    String firstLine = br.readLine();
                    if (firstLine.contains("|")) {
                        delimiter = '|';
                    }
                    if (firstLine.contains("\t")) {
                        delimiter = '\t';
                    }
                }catch(Exception e) {
                    try (BufferedReader br = Files.newBufferedReader(Paths.get(uploadedFile.getPath()), StandardCharsets.ISO_8859_1)) {
                        fileEncoding = "ISO_8859_1";
                        // grab the first line to check on delimiters
                        String firstLine = br.readLine();
                        if (firstLine.contains("|")) {
                            delimiter = '|';
                        }
                        if (firstLine.contains("\t")) {
                            delimiter = '\t';
                        }

                    }
                }
            }
            CsvSchema schema = csvMapper.schemaFor(String[].class)
                    .withColumnSeparator(delimiter)
                    .withLineSeparator("\n");
            if (delimiter == '\t') {
                schema = schema.withoutQuoteChar();
            }else if(delimiter == ','){
                schema = schema.withQuoteChar('"');
            }
            int topRow = 0;
            org.apache.poi.ss.usermodel.Name topRowRegion = BookUtils.getName(ppBook, "az_toprow");
            if (topRowRegion != null) {
                try {
                    topRow = Integer.parseInt(getACellValue(inputSheet, new AreaReference(topRowRegion.getRefersToFormula(), null))) - 1;
                } catch (Exception e) {
                    // if they have not written a number, assume 0
                }
            }

            MappingIterator<String[]> lineIterator = null;
            if (fileEncoding != null) {
                // so override file encoding.
                lineIterator = csvMapper.readerFor(String[].class).with(schema).readValues(new InputStreamReader(new FileInputStream(filePath), fileEncoding));
                uploadedFile.clearParameter(FILEENCODING);//the converted file is in UTF-8
            } else {
                lineIterator = (csvMapper.readerFor(String[].class).with(schema).readValues(new File(filePath)));
            }


            Map<Integer, String> inputColumns = new HashMap();
            int inputHeadingCount = 0;
            String heading = getCellValue(inputSheet,headingStartRow + existingHeadingRows - 1,inputHeadingCount);
            int lastCellNum = inputSheet.getRow(headingStartRow).getLastCellNum();
            while (inputHeadingCount <= lastCellNum) {
                if (heading.length() > 0) {
                    heading = "";
                    for (int row = 0; row < existingHeadingRows; row++) {
                        heading += getCellValue(inputSheet,headingStartRow,inputHeadingCount);

                    }
                    heading = headingFrom(heading, headingsLookups);

                    inputColumns.put(inputHeadingCount, heading);

                }
                heading = getCellValue(inputSheet,headingStartRow + existingHeadingRows - 1,++inputHeadingCount);
            }
            int lastOutputCol = outputSheet.getRow(outputRow).getLastCellNum();
            while (getCellValue(outputSheet,outputRow,lastOutputCol + 1).length() > 0) {
                lastOutputCol++;
            }
            //Map <Integer,Integer> colOnInputRange = new HashMap<>();
            boolean isNewHeadings = true;
            Map<Integer, Integer> inputColumnMap = new HashMap<>();
            int lineNo = 0;
            int headingsFound = 0;
            int backwardCount = 0;
            List<String[]> backwardLines = new ArrayList<>();
            String[] lastline = null;
            int blankRows = 0;
            while (lineIterator.hasNext() || backwardCount > 0) {
                while (!isNewHeadings && lineNo < topRow && lineIterator.hasNext()) {
                    lineIterator.next();
                    lineNo++;
                }
                if (!lineIterator.hasNext() && backwardCount==0) {
                    break;
                }
                clearRow(inputSheet.getRow(inputRowNo));
                String[] line = null;
                if (backwardCount > 0)
                    line = backwardLines.get(--backwardCount);
                else {
                    line = lineIterator.next();
                }
                if (blankRows++==40){//arbitrary
                    break;
                }
                for (int i=0;i<line.length;i++){
                    if (line[i].length() > 0){
                        blankRows = 0;
                        break;
                    }
                 }
                if (blankRows == 0){
                    int colNo = 0;
                    //boolean validLine = true;
                    //INTERIM CHECK FOR HEADINGS ON THE WRONG LINE  - THIS DOES NOT WORK FOR HEADINGS BELOW WHERE EXPECTED
                    //ALSO SHOULD PROBABLY CHECK MORE THAN ONE CELL.
                    if (isNewHeadings && checkHeadings(inputColumns, line, headingsLookups)) {
                        headingStartRow = lineNo;

                    }
                    if (lineNo < headingStartRow) {
                        for (String cellVal : line) {
                            setCellValue(inputSheet, lineNo, colNo, cellVal);

                            colNo++;
                        }
                    } else {
                        if (isNewHeadings) {
                            boolean hasHeadings = false;
                            while (!hasHeadings) {
                                hasHeadings = checkHeadings(inputColumns, line, headingsLookups);
                                if (!hasHeadings) {
                                    if (lineIterator.hasNext()) {
                                        line = lineIterator.next();
                                    } else {
                                        return;
                                    }
                                }
                            }
                            //read off all the headings.  If there is more than one line of headings, then all but the last
                            // line inherit headings from the columns to the left.
                            //first build an array of strings, then concatenate each column and look up in the headingslookups
                            List<List<String>> newHeadings = new ArrayList<>();
                            for (int col = 0; col < line.length; col++) {
                                List<String> newHeading = new ArrayList<>();
                                String cellVal = line[col];
                                if (existingHeadingRows > 1 && cellVal == "" && col > 0) {
                                    cellVal = newHeadings.get(col - 1).get(0);
                                } else {
                                    if (cellVal == "" && lastline != null && lastline.length > col) {
                                        cellVal = lastline[col];
                                    }
                                }
                                newHeading.add(cellVal);
                                newHeadings.add(newHeading);
                            }
                            for (int headingRow = 1; headingRow < existingHeadingRows; headingRow++) {
                                if (!lineIterator.hasNext()) {
                                    break;
                                }
                                line = lineIterator.next();
                                for (int col = 0; col < line.length; col++) {
                                    String cellVal = line[col];
                                    //carry through values except on the last row
                                    if (existingHeadingRows > 1 && headingRow < existingHeadingRows - 1 && cellVal == "" && col > 0) {
                                        cellVal = newHeadings.get(col - 1).get(headingRow);
                                    }
                                    if (col >= newHeadings.size()) {//if the next line is longer, copy the last cell
                                        newHeadings.add(newHeadings.get(col - 1));
                                        newHeadings.get(col).set(headingRow, cellVal);
                                    } else {
                                        newHeadings.get(col).add(cellVal);
                                    }
                                }
                            }
                            List<String> newMappedHeadings = new ArrayList<>();
                            for (int col = 0; col < newHeadings.size(); col++) {
                                String lastheadingLine = newHeadings.get(col).get(existingHeadingRows - 1);
                                String newHeading = "";
                                if (lastheadingLine.length() > 0) {
                                    for (int row = 0; row < existingHeadingRows; row++) {
                                        newHeading += newHeadings.get(col).get(row);
                                    }
                                    newHeading = headingFrom(newHeading, headingsLookups);
                                }
                                newMappedHeadings.add(newHeading);


                            }
                            for (int col = 0; col < newHeadings.size(); col++) {
                                Integer targetCol = findFirst(inputColumns, newMappedHeadings.get(col));
                                //check that the last row of headings is not blank, then look it up
                                if (newHeadings.get(col).get(existingHeadingRows - 1).length() > 0 && targetCol >= 0) {
                                    //note - ignores heading if no map found
                                    inputColumnMap.put(col, targetCol);
                                    inputColumns.put(targetCol, "---found---");
                                }
                            }
                            if (!lineIterator.hasNext()) {
                                break;
                            }
                            if (backwards){
                                while (lineIterator.hasNext()) {
                                    backwardLines.add(lineIterator.next());
                                }
                                backwardCount = backwardLines.size();
                                line = backwardLines.get(--backwardCount);

                            }else {
                                line = lineIterator.next();
                            }
                        }
                        //handle the data
                        for (colNo = 0; colNo < line.length; colNo++) {
                            if (inputColumnMap.get(colNo) != null) {
                               setCellValue(inputSheet, inputRowNo, inputColumnMap.get(colNo), line[colNo]);
                            }
                        }
                        for (String param : uploadedFile.getParameters().keySet()) {
                            Name name = getNameInSheet(ppBook, inputSheet.getSheetName(), param);
                            if (name != null) {
                                AreaReference areaRef = new AreaReference(name.getRefersToFormula(), null);
                                setCellValue(inputSheet, areaRef.getFirstCell().getRow(), areaRef.getFirstCell().getCol(), uploadedFile.getParameter(param));
                                //System.out.println("setting parameter in sheet" + name.getNameName());
                            }
                        }
                        if (fileNameAreaRef != null) {
                            setCellValue(inputSheet, fileNameAreaRef.getFirstCell().getRow(), fileNameAreaRef.getFirstCell().getCol(), fileName);
                        }
                        //long t = System.currentTimeMillis();
                        for (Name persistSource : persistNames.keySet()) {
                            evaluateAllFormulaCells(ppBook, persistSource);
                            AreaReference ar = new AreaReference(persistSource.getRefersToFormula(),null);
                            String persistString = getACellValue(inputSheet, ar);
                            if (persistString != null && persistString.length() > 0) {
                                AreaReference target = persistNames.get(persistSource);
                                setCellValue(inputSheet, target.getFirstCell().getRow(), target.getFirstCell().getCol(), persistString);
                            }
                        }

                        evaluateAllFormulaCells(ppBook, outputLineRegion);
                        //System.out.println("eval " + (System.currentTimeMillis()-t));

                        // EFC note - if you wan't full formula resolve on a bug switch on the poi logging options in
                        // SpreadsheetService and do something like this on the relevant cell
                        //FormulaEvaluator evaluator = ppBook.getCreationHelper().createFormulaEvaluator();
                        //evaluator.setDebugEvaluationOutputForNextEval(true);
                        //evaluator.evaluateFormulaCell(cell);


                    /*
                    if (fromDBArea!=null){
                        Sheet dbSheet = ppBook.getSheet(fromDBArea.getFirstCell().getSheetName());
                        int topDBRow = fromDBArea.getFirstCell().getRow();
                        for (int col = fromDBArea.getFirstCell().getCol();col <= fromDBArea.getLastCell().getCol();col++){
                            String dbAttribute = getCellValue(dbSheet,topDBRow,col);
                            String dbValue="";
                            if (dbAttribute.contains("`.`")){
                                DatabaseAccessToken databaseAccessToken = loggedInUser.getDataAccessToken();
                                try{
                                    dbValue = RMIClient.getServerInterface(databaseAccessToken.getServerIp()).getNameAttribute(databaseAccessToken, dbAttribute.substring(dbAttribute.indexOf("`"), dbAttribute.indexOf("`.`")), dbAttribute.substring( dbAttribute.indexOf("`.`") + 3, dbAttribute.lastIndexOf("`")));
                                } catch (Exception e){
                                    dbValue = e.getMessage(); // I guess give them a clue when it doesn't work
                                }
                            }
                            dbSheet.getRow(topDBRow+1).getCell(col).setCellValue(dbValue);
                        }

                       XSSFFormulaEvaluator.evaluateAllFormulaCells(ppBook);
                    }

                     */
                        boolean ignore = false;
                        if (ignoreRef != null){
                            evaluateAllFormulaCells(ppBook, ignoreRegion);
                            if(inputSheet.getRow(ignoreRef.getFirstCell().getRow()).getCell(ignoreRef.getFirstCell().getCol()).getBooleanCellValue()) {
                                ignore = true;
                            }
                        }
                          int outputCol = 0;

                        if (isNewHeadings) {
                            for (colNo = outputCol; colNo <= lastOutputCol; colNo++) {
                                String cellVal = getCellValue(outputSheet, outputRow, colNo);
                                if (colNo > 0) {
                                    fileWriter.write("\t" + normalise(cellVal));
                                } else {
                                    fileWriter.write(normalise(cellVal));
                                }
                            }
                            fileWriter.write("\r\n");
                            isNewHeadings = false;
                        }
                        if (!ignore) {
                            int oRow = outputRow + 1;
                            String firstOut = getCellValue(outputSheet, oRow, 0);
                            while (oRow == outputRow + 1 || (getCellValue(outputSheet, oRow, 0).length() > 0 && getCellValue(outputSheet, oRow, 0).equals(firstOut))) {
                                for (colNo = outputCol; colNo < lastOutputCol; colNo++) {
                                    String cellVal = getCellValue(outputSheet, oRow, colNo);
                                    if (colNo > 0) {
                                        fileWriter.write("\t" + normalise(cellVal));
                                    } else {
                                        if (normalise(cellVal).length() > 0) {
                                            fileWriter.write(normalise(cellVal));
                                        }
                                    }
                                }
                                oRow++;
                                fileWriter.write("\r\n");
                            }
                        }
                    }

                }
                lastline = line;
                if (lineNo % 200 == 0) {
                    try{
                        RMIClient.getServerInterface(loggedInUser.getDatabaseServer().getIp()).addToLog(loggedInUser.getDataAccessToken(), "Preprocessing line: " + lineNo );
                    }catch(Exception e){
                        //ignore - there is no log set up for a template test
                    }
                }
                lineNo++;
             }
            fileWriter.flush();
            fileWriter.close();
            uploadedFile.setPath(outFile);
            //opcPackage.revert();
            //debug lines below
            /*
            outFile = "c:\\users\\test\\Downloads\\Corrupt.xlsx";
            writeFile = new File(outFile);
            writeFile.delete(); // to avoid confusion

            OutputStream outputStream = new FileOutputStream(writeFile) ;
            ppBook.write(outputStream);

             */
             //end debug

            //opcPackage.revert();

         } catch (Exception e) {
            /*
            String outFile = "c:\\users\\test\\Downloads\\Corrupt.xlsx";
            File writeFile = new File(outFile);
            writeFile.delete(); // to avoid confusion

            OutputStream outputStream = new FileOutputStream(writeFile);
            ppBook.write(outputStream);
            opcPackage.revert();
           */
            throw e;
        }
    }

    private static void setUpPreprocessor(LoggedInUser loggedInUser, UploadedFile uploadedFile, String preprocessor)throws Exception{
        OPCPackage opcPackage = null;
        Workbook ppBook = null;
        try (FileInputStream fi = new FileInputStream(preprocessor)) { // this will hopefully guarantee that the file handler is released under windows
            opcPackage = OPCPackage.open(fi);
        } catch (Exception e) {
            e.printStackTrace();
            throw new Exception("Cannot load preprocessor template from " + preprocessor);
        }
        ppBook = new XSSFWorkbook(opcPackage);
        org.apache.poi.ss.usermodel.Name jsonRegion = BookUtils.getName(ppBook, "az_JSONRules");
        if (jsonRegion != null) {
            preProcessJSON(loggedInUser, uploadedFile, ppBook);
            opcPackage.revert();
            return;
        }
        org.apache.poi.ss.usermodel.Name inputLineRegion = BookUtils.getName(ppBook, "az_input");
        AreaReference inputAreaRef = new AreaReference(inputLineRegion.getRefersToFormula(), null);
        int inputRowNo = inputAreaRef.getLastCell().getRow();


        org.apache.poi.ss.usermodel.Name includesLineRegion = BookUtils.getName(ppBook, "az_includes");

        AreaReference includesAreaRef = null;
        if (includesLineRegion != null) {
            includesAreaRef = new AreaReference(includesLineRegion.getRefersToFormula(), null);
            Sheet includesSheet = ppBook.getSheet(includesLineRegion.getSheetName());

            for (int inRow = includesAreaRef.getFirstCell().getRow(); inRow <= includesAreaRef.getLastCell().getRow(); inRow++) {
                String sourceName = getCellValue(includesSheet, inRow, 0);
                String existingSheetName = getCellValue(includesSheet, inRow, 1);
                if (existingSheetName.length() > 0) {
                    Sheet includeSheet = ppBook.getSheet(existingSheetName);
                    if (includeSheet != null) {
                        //removeSheetAt does NOT remove the name! hence.
                        String newName = "deleted" + inRow;
                        ppBook.setSheetName(ppBook.getSheetIndex(existingSheetName), newName);
                        ppBook.removeSheetAt(ppBook.getSheetIndex(newName));
                    }
                }
            }
            cleanNames(ppBook);
            for (int inRow = includesAreaRef.getFirstCell().getRow(); inRow <= includesAreaRef.getLastCell().getRow(); inRow++) {
                org.apache.poi.xssf.usermodel.XSSFWorkbook includeBook = null;
                String sourceName = getCellValue(includesSheet, inRow, 0);
                OPCPackage opcPackageInclude = null;
                if (sourceName.length() > 0) {
                    if (sourceName.contains(".xls")) {
                        ImportTemplate includeFile = ImportTemplateDAO.findForNameAndBusinessId(sourceName, loggedInUser.getUser().getBusinessId());
                        try {
                            String includeFilePath = SpreadsheetService.getHomeDir() + dbPath + loggedInUser.getBusinessDirectory() + importTemplatesDir + includeFile.getFilenameForDisk();
                            try (FileInputStream fi = new FileInputStream(includeFilePath)) {
                                opcPackageInclude = OPCPackage.open(fi);
                            }
                            includeBook = new XSSFWorkbook(opcPackageInclude);

                        } catch (Exception e) {
                            e.printStackTrace();
                            throw new Exception("Cannot load include book: " + sourceName);
                        }
                    } else {
                        String reportName = sourceName.replace("_", " ");
                        if (reportName.contains(" with ")) {
                            String withContext = reportName.substring(reportName.indexOf(" with ") + 6);
                            ChoicesService.setChoices(loggedInUser, withContext);
                            reportName = reportName.substring(0, reportName.indexOf(" with ")).replace("`", "");
                        }
                        OnlineReport onlineReport = OnlineReportDAO.findForNameAndBusinessId(reportName, loggedInUser.getUser().getBusinessId());
                        if (onlineReport == null) {
                            return;
                        }
                        File file = ExcelService.createReport(loggedInUser, onlineReport, false);
                        opcPackageInclude = OPCPackage.open(file);
                        includeBook = new XSSFWorkbook(opcPackageInclude);


                    }
                    poiLocaliseNames(includeBook);
                    String insertName = includeBook.getSheetAt(0).getSheetName();
                    //TODO  Test for existing sheet with the name
                    Sheet newSheet = ppBook.createSheet(insertName);
                    ppBook.setSheetOrder(insertName, 1);//copying only one sheet - copy more???
                    PoiCopySheet.copySheet(includeBook.getSheetAt(0), ppBook.getSheetAt(1));
                    opcPackageInclude.revert();
                    newSheet = ppBook.getSheetAt(1);//maybe not needed?
                    String newSheetName = newSheet.getSheetName();
                    includesSheet.getRow(inRow).getCell(1).setCellValue(newSheetName);
                }

            }
            Map<String, String> sourceMap = new HashMap<>();
            extendList(ppBook, sourceMap, inputRowNo, true);
            extendList(ppBook, sourceMap, inputRowNo, false);
            try {
                XSSFFormulaEvaluator.evaluateAllFormulaCells(ppBook);
            } catch (Exception e) {
                // EFC note - there was a hardcoded path here that crashed on some servers, I have removed it
                opcPackage.revert();
                //e.printStackTrace(); // efc note - would we want to know this? Does stopping on a cell mean the rest arne't sorted?
                //seems to get hung up on some #refs which make little sense
            }

        }
        loggedInUser.setPreprocessorLoaded(ppBook);
        loggedInUser.setPreprocessorName(preprocessor);
        loggedInUser.setOpcPackage(opcPackage);

    }

    private static void poiLocaliseNames(XSSFWorkbook book){
        List<Name> toBeDeleted = new ArrayList<>();
        for (Name name : book.getAllNames()) {
            if (name.getSheetIndex()==-1) {
                name.setSheetIndex(book.getSheetIndex(name.getSheetName()));
                if (name.getSheetIndex()==-1){//the sheet name referred outside this book
                    toBeDeleted.add(name);
                }
            }
        }
        for (Name name:toBeDeleted){
            book.removeName(name);
        }

    }


    private static void extendList(Workbook ppBook, Map<String,String> sourceList,  int inputRowNo, boolean matching){
        List<Name> toBeDeleted = new ArrayList<>();
        for (Name name : ppBook.getAllNames()) {
            try {
                if (name.getNameName().startsWith("az_")) {
                    AreaReference nameArea = new AreaReference(name.getRefersToFormula(), null);
                    if (nameArea.getFirstCell()==nameArea.getLastCell()) {
                        CellReference cellRef = nameArea.getFirstCell();
                        Cell nameCell = makeCell(ppBook.getSheet(cellRef.getSheetName()),cellRef.getRow(),cellRef.getCol());
                        if ((name.getSheetIndex()==0&&nameCell.getRowIndex()==inputRowNo) ||(nameCell.getCellType() == FORMULA && !nameCell.getCellFormula().contains("deleted") && !nameCell.getCellFormula().endsWith(name.getNameName()))) {
                            if (matching) {
                                sourceList.put(name.getNameName(),name.getSheetName());
                            }
                        } else {
                            if (!matching && sourceList.get(name.getNameName())!=null) {
                                nameCell.setCellFormula("'" + sourceList.get(name.getNameName()) + "'!" + name.getNameName());
                            }
                        }
                    }

                }
            } catch (Exception e) {
                toBeDeleted.add(name);
            }
        }
        for (Name name : toBeDeleted) {
            ppBook.removeName(name);
        }
    }



    private static boolean nameApplies(String possibleName, String toTest){
        if (possibleName.endsWith("*")) {
            if (possibleName.startsWith("*")) {
                return (toTest.contains(possibleName.substring(1, possibleName.length() - 1)));
            } else {
                return (toTest.startsWith(possibleName.substring(0,possibleName.length()-1)));
            }
        }
        if (possibleName.startsWith("*")) {
            return toTest.endsWith(possibleName.substring(1));
        }
        return toTest.equals(possibleName);

    }



    private static List<String> getList(Workbook book, String rangeName) {

        List<String> toReturn = new ArrayList<>();
        try {
            org.apache.poi.ss.usermodel.Name region = BookUtils.getName(book, rangeName);
            if (region == null) return toReturn;

            AreaReference area = new AreaReference(region.getRefersToFormula(), null);
            for (int rowNo = area.getFirstCell().getRow(); rowNo <= area.getLastCell().getRow(); rowNo++) {
                String cellVal = getCellValue(book.getSheet(region.getSheetName()),rowNo,area.getFirstCell().getCol());
                if (cellVal != null) {
                    toReturn.add(cellVal.toLowerCase(Locale.ROOT));
                }
            }
        } catch (Exception e) {
            //ignore at present
        }
        return toReturn;
    }

    private static boolean checkHeadings(Map<Integer, String> headingsMap, String[] line, Map<String, String> headingsLookups) {

        if (line.length < 5) return false;
        //found five within the first ten
        int found = 0;
        int maxpos = line.length;

        for (int col = 0; col < maxpos; col++) {
            if (line[col].length() > 0 && findFirst(headingsMap, headingFrom(line[col], headingsLookups)) >= 0) {
                found++;
                if (found > 10 || found==headingsMap.size()) {//arbitrary at this stage.  Checking later
                    return true;
                }
            }
        }
        return false;

    }


    private static Map<String, String> setupHeadingsMappings(Workbook ppBook) {
        Map<String, String> headingsLookups = new HashMap<>();
        Sheet sheet = ppBook.getSheet("HeadingsLookups");
        if (sheet == null) {
            return headingsLookups;
        }
        org.apache.poi.ss.usermodel.Name headingsLookupsRegion = BookUtils.getName(ppBook, "az_HeadingsLookups");
        if (headingsLookupsRegion == null) {
            return headingsLookups;
        }
        Sheet hSheet = ppBook.getSheet(headingsLookupsRegion.getSheetName());
        AreaReference nameArea = new AreaReference(headingsLookupsRegion.getRefersToFormula(), null);
        //CellReference cellRef = nameArea.getFirstCell();
        int firstCol = nameArea.getFirstCell().getCol();
        int lastRow = nameArea.getLastCell().getRow();
        for (int rowNo = nameArea.getFirstCell().getRow(); rowNo <= lastRow; rowNo++) {
            String source = standardise(getCellValue(hSheet,rowNo,firstCol));
            String target = standardise(getCellValue(hSheet,rowNo,firstCol + 1));
            if (headingsLookups.get(source) != null) {
                if (headingsLookups.get(target) != null && !headingsLookups.get(target).equals(headingsLookups.get(source))){
                    //need to consolidate existing as target
                    for (String existingLookup:headingsLookups.keySet()){
                        if (headingsLookups.get(existingLookup).equals(headingsLookups.get(source))){
                            headingsLookups.put(existingLookup,headingsLookups.get(target));
                        }
                    }
                }
                headingsLookups.put(target, headingsLookups.get(source));
            } else {
                if (headingsLookups.get(target) != null) {
                    headingsLookups.put(source, headingsLookups.get(target));
                } else {
                    String targetRow = source + "," + target;
                    headingsLookups.put(source, targetRow);
                    headingsLookups.put(target, targetRow);
                }
            }
        }

        return headingsLookups;

    }

    //building up a list of mapping regions from xxx_persist to xxx (for values that only occur sporadically)
    public static Map<org.apache.poi.ss.usermodel.Name, AreaReference> getPersistNames(org.apache.poi.ss.usermodel.Workbook book) {
        Map<Name, AreaReference> toReturn = new HashMap<>();
        for (org.apache.poi.ss.usermodel.Name name : book.getAllNames()) {
            if (name.getNameName().toLowerCase().endsWith("_persist")) {

                String targetName = name.getNameName().substring(0, name.getNameName().length() - 8);
                org.apache.poi.ss.usermodel.Name targetRegion = BookUtils.getName(book, targetName);
                AreaReference target = new AreaReference(targetRegion.getRefersToFormula(), null);
                toReturn.put(name, target);
            }
        }
        return toReturn;
    }

    private static void cleanNames(Workbook ppBook) {
        List<Name> toBeDeleted = new ArrayList<>();
        for (Name name : ppBook.getAllNames()) {
            try {
                String ar = name.getRefersToFormula();

                if (name.getSheetName().startsWith("deleted")){
                    toBeDeleted.add(name);
                }
                try {
                    //and zap the formula if it refers to a deleted sheet.
                    CellReference nameCellRef = new AreaReference(ar, null).getFirstCell();
                    Cell nameCell = ppBook.getSheet(name.getSheetName()).getRow(nameCellRef.getRow()).getCell(nameCellRef.getCol());
                    if (nameCell.getCellType().equals(FORMULA) && nameCell.getCellFormula().contains("deleted")) {
                        nameCell.removeFormula();
                    }
                }catch(Exception e){
                    //ignore error here...
                }

            } catch (Exception e) {
                toBeDeleted.add(name);
            }
        }
        for (Name name : toBeDeleted) {
            ppBook.removeName(name);
        }
    }

    private static int findFirst(Map<Integer, String> map, String toFind) {
        for (int i : map.keySet()) {
            if (map.get(i).equals(toFind)) {
                return i;
            }
        }
        return -1;
    }


    private static Cell makeCell(Sheet sheet, int row, int col){
        Row targetRow = sheet.getRow(row);
        if (targetRow == null) {
            targetRow = sheet.createRow(row);
        }
        Cell targetCell = sheet.getRow(row).getCell(col);
        if (targetCell == null) {
            return sheet.getRow(row).createCell(col);
        }
        return targetCell;

    }

    public static void setCellValue(Sheet sheet, int row, int col, String cellVal) {
        Cell targetCell = makeCell(sheet,row, col);
        if (cellVal != null && cellVal.length() == 0) {
            cellVal = null;
        }
        if (cellVal != null && targetCell.getCellStyle().getDataFormatString() != "@" && DateUtils.isADate(cellVal) != null) {
            targetCell.setCellValue((double) DateUtils.excelDate(DateUtils.isADate(cellVal)));
        } else {
            //isNumber returns 'true' for cellVal = "16L", then parseDouble exceptions
            try {
                targetCell.setCellValue(Double.parseDouble(cellVal.replace(",", "")));
            } catch (Exception e) {
                targetCell.setCellValue(cellVal);
            }
            /*
             if (NumberUtils.isNumber(cellVal)) {
               targetCell.setCellValue(Double.parseDouble(cellVal));
            } else {
                targetCell.setCellValue(cellVal);
            }

             */
        }

    }

    private static Name getNameInSheet(Workbook book, String sheetName, String nameGiven) {
        for (Name name : book.getAllNames()) {
            if (name.getNameName().equalsIgnoreCase(nameGiven) && name.getSheetName().equals(sheetName)) {
                if (name.getRefersToFormula().startsWith("#")) {
                    return null;
                }
                return name;
            }

        }
        return null;
    }

    private static void clearRow(Row row) {
        int lastCol = row.getLastCellNum();
        for (int col = 0; col <= lastCol; col++) {
            Cell cell = row.getCell(col);
            if (cell != null) {
                CellStyle cs = cell.getCellStyle();
                row.removeCell(cell);
                cell = row.createCell(col);
                cell.setCellStyle(cs);

            }
        }
    }

    private static String headingFrom(String value, Map<String, String> lookup) {
        value = standardise(value);
        String map = lookup.get(value);
        if (map != null) {
            return map;
        }
        return value;
    }

    private static String standardise(String value) {
        //not sure how the system read the cr as \\n
        return normalise(value).toLowerCase(Locale.ROOT).replace(" ", "");
    }


    private static String normalise(String value) {
        //not sure how the system read the cr as \\n
        return value.replace("\\\\n", " ").replace("\n", " ").replace("\r", " ").replace("  ", " ").replace("_","");
    }

    // todo factor. Makes sense in here
    public static void preprocesorTest(MultipartFile[] preprocessorTest, ModelMap model, LoggedInUser loggedInUser) {
        MultipartFile zip = null;
        MultipartFile preProcessor = null;
        if (preprocessorTest.length == 2) {
            if (preprocessorTest[0].getOriginalFilename().toLowerCase().endsWith(".xlsx")) {
                preProcessor = preprocessorTest[0];
            }
            if (preprocessorTest[1].getOriginalFilename().toLowerCase().endsWith(".xlsx")) {
                preProcessor = preprocessorTest[1];
            }
            if (preprocessorTest[0].getOriginalFilename().toLowerCase().endsWith(".zip")) {
                zip = preprocessorTest[0];
            }
            if (preprocessorTest[1].getOriginalFilename().toLowerCase().endsWith(".zip")) {
                zip = preprocessorTest[1];
            }
        }
        if (zip == null || preProcessor == null) {
            model.put("error", "preprocessor upload test requires a zip and an XLSX file");
        } else { // ok try to load the files
            try {
                String preprocessorName = preProcessor.getOriginalFilename();
                File preprocessorTempLocation = new File(SpreadsheetService.getHomeDir() + "/temp/" + System.currentTimeMillis() + preprocessorName); // timestamp to stop file overwriting
                preProcessor.transferTo(preprocessorTempLocation);

                String fileName = zip.getOriginalFilename();
                // always move uploaded files now, they'll need to be transferred to the DB server after code split
                File moved = new File(SpreadsheetService.getHomeDir() + "/temp/" + System.currentTimeMillis() + fileName); // timestamp to stop file overwriting
                zip.transferTo(moved);

                ZipUtil.explode(new File(moved.getPath()));
                // after exploding the original file is replaced with a directory
                File zipDir = new File(moved.getPath());
                zipDir.deleteOnExit();
                // todo - go to Files.list()?
                List<File> files = new ArrayList<>(FileUtils.listFiles(zipDir, null, true));
                Map<String, String> fileNameParams = new HashMap<>();
                ImportService.addFileNameParametersToMap(fileName, fileNameParams);
                Path zipforuploadresult = Files.createTempDirectory("preprocessortestresult");

                for (File f : files) {
                    ImportService.addFileNameParametersToMap(f.getName(), fileNameParams);
                    UploadedFile zipEntryUploadFile = new UploadedFile(f.getPath(), Collections.singletonList(f.getName()), fileNameParams, false, false);
                    // ok I need to convert the excel input files here hhhhhhhngh
                    org.apache.poi.ss.usermodel.Workbook book;
                    if (!f.getName().endsWith(".xlsx") && !f.getName().endsWith(".xls")) {
                        UploadedFile uf = new UploadedFile(f.getPath(), zipEntryUploadFile.getFileNames(), fileNameParams, true, false);
                        ImportService.preProcessUsingPoi(loggedInUser, uf, preprocessorTempLocation.getPath());
                        String name = uf.getPath();
                        if (name.contains("/")) {
                            name = name.substring(name.lastIndexOf("/") + 1);
                        } else if (name.contains("\\")) {
                            name = name.substring(name.lastIndexOf("\\") + 1);
                        }
                        if (name.endsWith(" converted")) {
                            name = name.substring(0, name.length() - 10);
                            // now try to zap the last number
                            int timestampLength = (System.currentTimeMillis() + "").length();
                            if (name.endsWith(".tsv")) {
                                name = name.substring(0, name.length() - (timestampLength + 4)) + ".tsv";
                            }
                        }
                        Files.copy(Paths.get(uf.getPath()), zipforuploadresult.resolve(name));
                    } else {
                        try {
                            if (f.getName().endsWith(".xlsx")){
                                book = new org.apache.poi.xssf.usermodel.XSSFWorkbook(org.apache.poi.openxml4j.opc.OPCPackage.open(new FileInputStream(new File(zipEntryUploadFile.getPath()))));
                            }else{
                                book = new org.apache.poi.hssf.usermodel.HSSFWorkbook(new FileInputStream(new File(zipEntryUploadFile.getPath())));

                            }
                        } catch (org.apache.poi.openxml4j.exceptions.InvalidFormatException ife) {
                            // Hanover may send 'em encrypted
                            POIFSFileSystem fileSystem = new POIFSFileSystem(new FileInputStream(zipEntryUploadFile.getPath()));
                            EncryptionInfo info = new EncryptionInfo(fileSystem);
                            Decryptor decryptor = Decryptor.getInstance(info);
                            String password = zipEntryUploadFile.getParameter("password") != null ? zipEntryUploadFile.getParameter("password") : "b0702"; // defaulting to an old Hanover password. Maybe zap . . .
                            if (!decryptor.verifyPassword(password)) { // currently hardcoded, this will change
                                throw new RuntimeException("Unable to process: document is encrypted.");
                            }
                            InputStream dataStream = decryptor.getDataStream(fileSystem);
                            book = new org.apache.poi.xssf.usermodel.XSSFWorkbook(dataStream);
                        }

                        for (int sheetNo = 0; sheetNo < book.getNumberOfSheets(); sheetNo++) {
                            org.apache.poi.ss.usermodel.Sheet sheet = book.getSheetAt(sheetNo);

                            File temp = File.createTempFile(f.getPath() + sheet.getSheetName(), ".tsv");
                            String tempPath = temp.getPath();
                            temp.deleteOnExit();
                            FileOutputStream fos = new FileOutputStream(tempPath);
                            CsvWriter csvW = new CsvWriter(fos, '\t', StandardCharsets.UTF_8);
                            csvW.setUseTextQualifier(false);
                            // poi convert - notably the iterators skip blank rows and cells hence the checking that indexes match
                            int rowIndex = -1;
                            boolean emptySheet = true;
                            for (org.apache.poi.ss.usermodel.Row row : sheet) {
                                emptySheet = false;
                                // turns out blank lines are important
                                if (++rowIndex != row.getRowNum()) {
                                    while (rowIndex != row.getRowNum()) {
                                        csvW.endRecord();
                                        rowIndex++;
                                    }
                                }
                                int cellIndex = -1;
                                for (Iterator<org.apache.poi.ss.usermodel.Cell> ri = row.cellIterator(); ri.hasNext(); ) {
                                    org.apache.poi.ss.usermodel.Cell cell = ri.next();
                                    if (++cellIndex != cell.getColumnIndex()) {
                                        while (cellIndex != cell.getColumnIndex()) {
                                            if (!sheet.isColumnHidden(cellIndex)) {
                                                csvW.write("");
                                            }
                                            cellIndex++;
                                        }
                                    }
                                    final String cellValue = ImportService.getCellValue(sheet, cell.getRowIndex(), cell.getColumnIndex());//this is slightly odd
                                    if (!sheet.isColumnHidden(cellIndex)) {
                                        csvW.write(cellValue.replace("\n", "\\\\n").replace("\r", "")
                                                .replace("\t", "\\\\t"));
                                    }
                                }
                                csvW.endRecord();
                            }
                            csvW.close();
                            fos.close();
                            if (!emptySheet) {
                                UploadedFile uf = new UploadedFile(tempPath, zipEntryUploadFile.getFileNames(), fileNameParams, true, false);
                                ImportService.preProcessUsingPoi(loggedInUser, uf, preprocessorTempLocation.getPath());
                                String name = uf.getPath();
                                if (name.contains("/")) {
                                    name = name.substring(name.lastIndexOf("/") + 1);
                                } else if (name.contains("\\")) {
                                    name = name.substring(name.lastIndexOf("\\") + 1);
                                }
                                if (name.endsWith(" converted")) {
                                    name = name.substring(0, name.length() - 10);
                                    // now try to zap the last number
                                    int timestampLength = (System.currentTimeMillis() + "").length();
                                    if (name.endsWith(".tsv")) {
                                        name = name.substring(0, name.length() - (timestampLength + 4)) + ".tsv";
                                    }
                                }
                                Files.copy(Paths.get(uf.getPath()), zipforuploadresult.resolve(name));

                            }
                        }
                    }
                }
                ZipUtil.unexplode(zipforuploadresult.toFile());
                loggedInUser.setLastFile(zipforuploadresult.toString());
                if (loggedInUser.getOpcPackage()!=null){
                    loggedInUser.setPreprocessorLoaded(null);
                    loggedInUser.getOpcPackage().revert();
                    loggedInUser.setOpcPackage(null);
                }
                loggedInUser.setLastFileName(zipforuploadresult.getFileName().toString() + ".zip");
                // should probably not be HTML in here . . .
                model.put("results", "<a href=\"/api/Download?lastFile=true\">DOWNLOAD pre-processor test results " + zipforuploadresult.getFileName().toString() + ".zip" + "</a>");


            } catch (Exception e) {

                e.printStackTrace();
                model.put("error", e.getMessage());
            }

        }

    }
    public static void preProcessJSON(LoggedInUser loggedInUser, UploadedFile uploadedFile, Workbook ppBook) throws Exception {

        HashMap<String, ImportTemplateData> templateCache = new HashMap<>();
         org.apache.poi.ss.usermodel.Name jsonRulesRegion = BookUtils.getName(ppBook, "az_jsonrules");
        AreaReference jsonRulesAreaRef = new AreaReference(jsonRulesRegion.getRefersToFormula(), null);
        Sheet jSheet = ppBook.getSheet(jsonRulesRegion.getSheetName());

        int firstCol = jsonRulesAreaRef.getFirstCell().getCol();
        int lastRow = jsonRulesAreaRef.getLastCell().getRow();
        List<JsonRule> jsonRules = new ArrayList<>();
        Set<String> relevantPaths = new HashSet();
        for (int rowNo = jsonRulesAreaRef.getFirstCell().getRow(); rowNo <= lastRow; rowNo++) {
            Row row = jSheet.getRow(rowNo);
            String first = getCellValue(jSheet, rowNo, firstCol);
            JsonRule jsonRule = new JsonRule(first, getCellValue(jSheet, rowNo, firstCol + 1), getCellValue(jSheet, rowNo, firstCol + 2), getCellValue(jSheet, rowNo, firstCol + 3));
            jsonRules.add(jsonRule);
            List<String> jPath = Arrays.asList(jsonRule.sourceTerm.split(JSONFIELDDIVIDER));
            relevantPaths.add(jPath.get(0));
            String path = jPath.get(0);
            for (int i = 0; i < jPath.size(); i++) {
                path += JSONFIELDDIVIDER + jPath.get(i);
                relevantPaths.add(path);
            }
        }
        try {
            String outFile = uploadedFile.getPath() + " converted";
            File writeFile = new File(outFile);
            writeFile.delete(); // to avoid confusion
            BufferedWriter fileWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outFile), StandardCharsets.UTF_8));
            for (JsonRule jsonRule:jsonRules){
                fileWriter.write(jsonRule.target + "\t");
            }
            fileWriter.write("\n");
            JSONArray jsonArray = readJSON(uploadedFile.getPath(),null,null);
            for (Object o : jsonArray) {
                JSONObject jsonObject = (JSONObject) o;
                for (JsonRule jsonRule:jsonRules) {
                    boolean useRule = true;
                    if (jsonRule.condition!=null && jsonRule.condition.startsWith("only")){
                        useRule = hasCondition(jsonRules, jsonRule.condition);
                    }
                    if (useRule) {
                        String[] jsonPath = jsonRule.sourceTerm.split("\\" + JSONFIELDDIVIDER);

                        traverseJSON(jsonRule, jsonPath, jsonObject, 0, jsonRules);
                    }
                }
                int maxRecord = 0;
                for (JsonRule jsonRule:jsonRules){
                    if (jsonRule.found.size() > maxRecord){
                        maxRecord = jsonRule.found.size();
                    }
                }

                for (int outRecord = 0;outRecord < maxRecord; outRecord++){
                    for (JsonRule jsonRule:jsonRules){
                        String outvalue = null;
                        if (outRecord < jsonRule.found.size()){
                            outvalue = jsonRule.found.get(outRecord);
                        }else{
                            if (jsonRule.found.size() > 0){
                                outvalue = jsonRule.found.get(jsonRule.found.size()-1);
                            }
                        }
                        if (outvalue!=null && jsonRule.format.length() > 0) {
                            if (jsonRule.format.equalsIgnoreCase("text")){
                                //remove line feeds and tabs
                                outvalue = outvalue.replace("\t"," ").replace("\n",";").replace(";;",";");
                            }
                            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
                            int zonePos = jsonRule.format.toLowerCase().indexOf("timezone");
                            if (zonePos > 0){
                                sdf.setTimeZone(TimeZone.getTimeZone(jsonRule.format.substring(zonePos+8).trim()));

                            }
                            try {
                                if (jsonRule.format.startsWith("seconds")) {
                                    outvalue = sdf.format(new Date(Long.parseLong(outvalue)*1000));

                                }else {
                                    if (jsonRule.format.startsWith("milliseconds")) {
                                        outvalue = sdf.format(new Date(Long.parseLong(outvalue)));
                                    }
                                }

                            }catch (Exception e2){
                                //leave outvalue as it is
                            }
                         }
                        if (outvalue!=null &&outvalue.length() > 0){
                            fileWriter.write(zapCRs(outvalue));
                        }
                        fileWriter.write("\t");
                    }
                    fileWriter.write("\n");
                }
                for (JsonRule jsonRule:jsonRules) {
                    jsonRule.found = new ArrayList<>();
                }
            }

            fileWriter.flush();
            fileWriter.close();
            uploadedFile.setPath(outFile);
            readPreparedFile(loggedInUser, uploadedFile, false, null, templateCache);


        } catch (Exception e) {
            //todo - report exceptions

        }
    }

    public static String zapCRs(String text){
        return text.replace("\n", "\\\\n").replace("\r", "").replace("\t", "\\\\t");
    }

    public static boolean traverseJSON(JsonRule jsonRule,String[] jsonPath,JSONObject jsonNext, int level, List<JsonRule>  jsonRules) throws Exception{
        if (level < jsonPath.length - 1) {
            JSONArray jsonArray1 = new JSONArray();
            try {
                JSONObject jsonNext1 = jsonNext.getJSONObject(jsonPath[level]);
                jsonArray1.put(jsonNext1);
            } catch (Exception e) {
                try{
                    jsonArray1 = jsonNext.getJSONArray(jsonPath[level]);
                }catch(Exception e2){
                    String path = "";
                    for (int l=0;l<=level;l++){
                        path= path+"/"+jsonPath[l];
                    }
                    jsonRule.found.add("");
                    return false;
                }
            }
            level++;
            if (jsonArray1 != null) {
                for (Object o1 : jsonArray1) {
                    jsonNext = (JSONObject) o1;
                    if (!traverseJSON(jsonRule, jsonPath, jsonNext, level, jsonRules)){
                        break;
                    };
                }
            }
            return true;
        }
        String found = null;
        try {
            found = jsonNext.get(jsonPath[level]).toString();
        } catch (Exception e) {
        }
        if(found==null){
            return true;
        }
        if (jsonRule.condition.length() > 0) {
            if (jsonRule.condition.toLowerCase(Locale.ROOT).startsWith("regex ")) {
                if (!Pattern.matches(jsonRule.condition.substring(6).trim(), found)){
                    found = null;
                }
            }
            if (jsonRule.condition.toLowerCase(Locale.ROOT).startsWith("where")) {
                String condition = jsonRule.condition.substring(6).trim();
                int equalPos = condition.indexOf("=");

                String compFound = jsonNext.get(condition.substring(0, equalPos).trim()).toString();
                if (compFound == null || !compFound.equalsIgnoreCase(condition.substring(equalPos + 1).trim())) {
                    found = null;
                }
            }
        }
        if (found != null) {
            jsonRule.found.add(found);
        }
        return true;
    }

    public static boolean hasCondition(List<JsonRule>jsonRules, String condition){
        if(condition.toLowerCase(Locale.ROOT).startsWith("only ")) {
            condition = condition.substring(5);
        }
        for (JsonRule jsonRule1:jsonRules){
            if (jsonRule1.target.equalsIgnoreCase(condition)  && jsonRule1.found.size()>0){
                return true;
            }
        }
        return false;
    }




    public static JSONArray readJSON(String filePath, String page_size, String cursor)
            throws Exception {
        String data = new String(Files.readAllBytes(Paths.get(filePath)), Charset.defaultCharset());
        //strip away spurious fields
        try{
            data = data.substring(data.indexOf("["), data.lastIndexOf("]")+1);
        }catch(Exception e){
            throw new Error("no array of data in JSON");
        }
         JSONArray jsonArray = null;
        try {
            jsonArray = new JSONArray(data.replace("\n",""));//remove line feeds
        } catch (Exception e) {
            e.printStackTrace();
        }
        return jsonArray;
    }



    public static void evaluateAllFormulaCells(Workbook wb, Name name) {
        FormulaEvaluator evaluator = wb.getCreationHelper().createFormulaEvaluator();
        AreaReference ar = new AreaReference(name.getRefersToFormula(),null);
        try {
            Sheet sheet = wb.getSheet(ar.getFirstCell().getSheetName());

            for (int rowNo = ar.getFirstCell().getRow(); rowNo <= ar.getLastCell().getRow(); rowNo++) {
                if (sheet.getRow(rowNo) != null) {
                    for (int colNo = ar.getFirstCell().getCol(); colNo <= ar.getLastCell().getCol(); colNo++) {
                        Cell c = sheet.getRow(rowNo).getCell(colNo);
                        if (c != null && c.getCellType() == CellType.FORMULA) {
                            evaluator.evaluateFormulaCell(c);
                        }
                    }
                }
            }
        }catch(Exception e){
            //dud reference
        }

    }

    public static String checkFileEncoding(String path)throws  IOException{
        try {
            checkFileEncoding1(path,null);
            return null;
        }catch(IOException e){
            try {
                String fileEncoding = "ISO_8859_1";
                checkFileEncoding1(path, fileEncoding);
                return fileEncoding;
            }catch(IOException e2){
                throw new IOException("cannot understand the file encoding");
            }
        }
    }

    public static void checkFileEncoding1(String path, String fileEncoding) throws IOException {
        int k=100;
        Charset charset = StandardCharsets.UTF_8;
        if ("ISO_8859_1".equals(fileEncoding)) {
            charset = StandardCharsets.ISO_8859_1;
        }
        List<String> lines = Files.readAllLines(Paths.get(path), charset);
        k = 1;

    }

}
