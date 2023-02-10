package com.azquo.dataimport;

import com.azquo.DateUtils;
import com.azquo.admin.onlinereport.OnlineReport;
import com.azquo.admin.onlinereport.OnlineReportDAO;
import com.azquo.rmi.RMIClient;
import com.azquo.spreadsheet.ExcelService;
import com.azquo.spreadsheet.LoggedInUser;
import com.azquo.spreadsheet.SpreadsheetService;
import com.azquo.spreadsheet.transport.UploadedFile;
import com.azquo.spreadsheet.zk.BookUtils;
import com.azquo.spreadsheet.zk.ChoicesService;
import com.csvreader.CsvWriter;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvParser;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import org.apache.commons.io.FileUtils;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.poifs.crypt.Decryptor;
import org.apache.poi.poifs.crypt.EncryptionInfo;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.poi.ss.formula.BaseFormulaEvaluator;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.AreaReference;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFFormulaEvaluator;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.ui.ModelMap;
import org.springframework.web.multipart.MultipartFile;
import org.zeroturnaround.zip.ZipUtil;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;

import static org.apache.poi.ss.usermodel.CellType.FORMULA;

public class Preprocessor {

    public static final String JSONFIELDDIVIDER = "|";


    // maybe redo at some point checking variable names etc but this is fine enough for the moment todo - further changes since then, EFC check
    public static List<com.azquo.dataimport.OutputFile> preProcessUsingPoi(LoggedInUser loggedInUser, UploadedFile uploadedFile, String preprocessor) throws Exception {
        List<OutputFile> outputFiles = new ArrayList<>();
        OPCPackage opcPackage = null;
        Workbook ppBook = null;


        try {
            // WARNING!!! EFC note - this cacheing of workbooks causes problems. I'm commenting, if uncommented without investigation PROBLEMS WILL RECUR
            //if (loggedInUser.getPreprocessorName()==null || !loggedInUser.getPreprocessorName().equals(preprocessor)) {
            setUpPreprocessor(loggedInUser, uploadedFile, preprocessor);
            //}

            ppBook = loggedInUser.getPreprocessorLoaded();
            if (ppBook == null) {//json
                return outputFiles;
            }
            List<String> ignoreSheetList = getList(ppBook, "az_IgnoreSheets");
            if (ignoreSheetList.size() > 0) {
                for (String ignoreSheet : ignoreSheetList) {
                    if (ignoreSheet.length() > 0) {
                        for (String fName : uploadedFile.getFileNames()) {
                            if (nameApplies(ignoreSheet.toLowerCase(Locale.ROOT), fName.toLowerCase(Locale.ROOT))) {
                                uploadedFile.setPath(null);
                                return outputFiles;
                            }
                        }
                    }
                }
            }
            List<String> useSheetList = getList(ppBook, "az_UseSheets");
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
                        return outputFiles;
                    }
                }
            }

            String filePath = uploadedFile.getPath();
            Map<String, String> headingsLookups = setupHeadingsMappings(ppBook);

            org.apache.poi.ss.usermodel.Name inputLineRegion = BookUtils.getName(ppBook, "az_input");
            AreaReference inputAreaRef = new AreaReference(inputLineRegion.getRefersToFormula(), null);
            int inputRowNo = inputAreaRef.getLastCell().getRow();
            List<org.apache.poi.ss.usermodel.Name> outputLineRegions = BookUtils.getNamesStarting(ppBook, ImportWizard.AZOUTPUT);
            for (org.apache.poi.ss.usermodel.Name outputLineRegion : outputLineRegions) {
                if (outputLineRegions.size()==1 || outputLineRegion.getNameName().length()>ImportWizard.AZOUTPUT.length()) {
                    AreaReference outputAreaRef = new AreaReference(outputLineRegion.getRefersToFormula(), null);
                    if (outputAreaRef.getFirstCell().getCol() == -1) {
                        int lastOutputCell = ppBook.getSheetAt(0).getRow(outputAreaRef.getFirstCell().getRow()).getLastCellNum();
                        outputLineRegion.setRefersToFormula("'" + ppBook.getSheetAt(0).getSheetName() + "'!" + BookUtils.rangeToText(outputAreaRef.getFirstCell().getRow(), 0) + ":" + BookUtils.rangeToText(outputAreaRef.getLastCell().getRow(), lastOutputCell));
                    }
                    String outputName = outputLineRegion.getNameName().substring(9);
                    int lastPathPos = filePath.lastIndexOf("/");
                    if (lastPathPos == -1) {
                        lastPathPos = filePath.lastIndexOf("\\");

                    }
                    String outputFileName = filePath.substring(0, lastPathPos + 1) + outputName + " " + filePath.substring(lastPathPos + 1) + " converted";
                    File outFile = new File(outputFileName);
                    outFile.delete(); // to avoid confusion
                    BufferedWriter fileWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outputFileName), StandardCharsets.UTF_8));

                    int lastOutputCol = 0;
                    Iterator it = ppBook.getSheetAt(0).getRow(outputAreaRef.getFirstCell().getRow()).cellIterator();
                    while (it.hasNext()){
                        Cell cell = (Cell)it.next();
                        try {
                            if (cell.getColumnIndex() > lastOutputCol && cell.getStringCellValue().length() > 0) {
                                lastOutputCol = cell.getColumnIndex();
                            }
                        }catch(Exception e){
                            //ignore numbers and blanks...
                        }
                    }
                    outputFiles.add(new OutputFile(outputName, outputLineRegion, outputAreaRef.getFirstCell().getRow(), lastOutputCol, outFile, fileWriter));//removing 'az_output' from the name;
                }
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
            Sheet outputSheet = inputSheet;//note this could be on another sheet, but, for the moment, I'm assuming the same sheet
            int headingStartRow = inputAreaRef.getFirstCell().getRow();
            int existingHeadingRows = inputAreaRef.getLastCell().getRow() - headingStartRow;
            CsvMapper csvMapper = new CsvMapper();
            csvMapper.enable(CsvParser.Feature.WRAP_AS_ARRAY);
            String fileEncoding = uploadedFile.getParameter(ImportService.FILEENCODING);
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
                } catch (Exception e) {
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
            String schemaParameter = uploadedFile.getTemplateParameter("schema");
            if ("withquotes".equals(schemaParameter)) {
                schema = schema.withQuoteChar('"');
            } else {
                if (delimiter == '\t') {
                    schema = schema.withoutQuoteChar();
                } else if (delimiter == ',') {
                    schema = schema.withQuoteChar('"');
                }
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
                uploadedFile.clearParameter(ImportService.FILEENCODING);//the converted file is in UTF-8
            } else {
                lineIterator = (csvMapper.readerFor(String[].class).with(schema).readValues(new File(filePath)));
            }


            Map<Integer, String> inputColumns = new HashMap();
            int inputHeadingCount = 0;
            String heading = getCellValue(inputSheet, headingStartRow + existingHeadingRows - 1, inputHeadingCount);
            int lastCellNum = inputSheet.getRow(headingStartRow).getLastCellNum();
            while (inputHeadingCount <= lastCellNum) {
                if (heading.length() > 0) {
                    heading = "";
                    for (int row = 0; row < existingHeadingRows; row++) {
                        heading += getCellValue(inputSheet, headingStartRow, inputHeadingCount);

                    }
                    heading = headingFrom(heading, headingsLookups);

                    inputColumns.put(inputHeadingCount, heading);

                }
                heading = getCellValue(inputSheet, headingStartRow + existingHeadingRows - 1, ++inputHeadingCount);
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
            if (fileNameAreaRef != null) {
                setCellValue(inputSheet, fileNameAreaRef.getFirstCell().getRow(), fileNameAreaRef.getFirstCell().getCol(), fileName);
            }
            BaseFormulaEvaluator.evaluateAllFormulaCells(ppBook);

            while (lineIterator.hasNext() || backwardCount > 0) {
                while (!isNewHeadings && lineNo < topRow && lineIterator.hasNext()) {
                    lineIterator.next();
                    lineNo++;
                }
                if (!lineIterator.hasNext() && backwardCount == 0) {
                    break;
                }
                clearRow(inputSheet.getRow(inputRowNo));
                String[] line = null;
                if (backwardCount > 0)
                    line = backwardLines.get(--backwardCount);
                else {
                    line = lineIterator.next();
                }
                if (blankRows++ == 40) {//arbitrary
                    break;
                }
                for (int i = 0; i < line.length; i++) {
                    if (line[i].length() > 0) {
                        blankRows = 0;
                        break;
                    }
                }
                if (blankRows == 0) {
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
                                        return outputFiles;
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
                            if (backwards) {
                                while (lineIterator.hasNext()) {
                                    backwardLines.add(lineIterator.next());
                                }
                                backwardCount = backwardLines.size();
                                line = backwardLines.get(--backwardCount);

                            } else {
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
                         //long t = System.currentTimeMillis();
                        for (Name persistSource : persistNames.keySet()) {
                            evaluateAllFormulaCells(ppBook, persistSource);
                            AreaReference ar = new AreaReference(persistSource.getRefersToFormula(), null);
                            String persistString = getACellValue(inputSheet, ar);
                            if (persistString != null && persistString.length() > 0) {
                                AreaReference target = persistNames.get(persistSource);
                                setCellValue(inputSheet, target.getFirstCell().getRow(), target.getFirstCell().getCol(), persistString);
                            }
                        }

                        for (OutputFile outputFile : outputFiles) {
                            evaluateAllFormulaCells(ppBook, outputFile.outputRegion);
                        }
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
                        if (ignoreRef != null) {
                            evaluateAllFormulaCells(ppBook, ignoreRegion);
                            if (inputSheet.getRow(ignoreRef.getFirstCell().getRow()).getCell(ignoreRef.getFirstCell().getCol()).getBooleanCellValue()) {
                                ignore = true;
                            }
                        }
                        int outputCol = 0;

                        if (isNewHeadings) {
                            for (OutputFile outputFile : outputFiles) {
                                for (colNo = outputCol; colNo <= outputFile.lastCol; colNo++) {
                                    String cellVal = getCellValue(outputSheet, outputFile.outputRow, colNo);
                                    if (colNo > 0) {
                                        outputFile.writer.write("\t" + normalise(cellVal));
                                    } else {
                                        outputFile.writer.write(normalise(cellVal));
                                    }
                                }
                                outputFile.writer.write("\r\n");
                            }
                            isNewHeadings = false;
                        }
                        if (!ignore) {
                            for (OutputFile outputFile : outputFiles) {
                                int oRow = outputFile.outputRow + 1;
                                String firstOut = getCellValue(outputSheet, oRow, 0);
                                while (oRow == outputFile.outputRow + 1 || (getCellValue(outputSheet, oRow, 0).length() > 0 && getCellValue(outputSheet, oRow, 0).equals(firstOut))) {
                                    //I'm not sure that the idea of outputting more than one row is useful, better to output different files.
                                    for (colNo = outputCol; colNo <= outputFile.lastCol; colNo++) {
                                        String cellVal = getCellValue(outputSheet, oRow, colNo);
                                        if (colNo > 0) {
                                            outputFile.writer.write("\t" + normalise(cellVal));
                                        } else {
                                            if (normalise(cellVal).length() > 0) {
                                                outputFile.writer.write(normalise(cellVal));
                                            }
                                        }
                                    }
                                    oRow++;
                                    outputFile.writer.write("\r\n");
                                }
                            }
                        }
                    }

                }
                lastline = line;
                if (lineNo % 200 == 0) {
                    try {
                        RMIClient.getServerInterface(loggedInUser.getDatabaseServer().getIp()).addToLog(loggedInUser.getDataAccessToken(), "Preprocessing line: " + lineNo);
                    } catch (Exception e) {
                        //ignore - there is no log set up for a template test
                    }
                }
                lineNo++;
            }
            for (OutputFile outputFile : outputFiles) {
                outputFile.writer.flush();
                outputFile.writer.close();
            }
            //debug lines below

            //String outFile = "c:\\users\\test\\Downloads\\Corrupt.xlsx";
            //File writeFile = new File(outFile);
            //writeFile.delete(); // to avoid confusion

            //OutputStream outputStream = new FileOutputStream(writeFile) ;
            //ppBook.write(outputStream);


            //end debug


            return outputFiles;

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


    private static void setUpPreprocessor(LoggedInUser loggedInUser, UploadedFile uploadedFile, String preprocessor) throws Exception {
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
                            String includeFilePath = SpreadsheetService.getHomeDir() + ImportService.dbPath + loggedInUser.getBusinessDirectory() + ImportService.importTemplatesDir + includeFile.getFilenameForDisk();
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
            String source = standardise(getCellValue(hSheet, rowNo, firstCol));
            String target = standardise(getCellValue(hSheet, rowNo, firstCol + 1));
            if (headingsLookups.get(source) != null) {
                if (headingsLookups.get(target) != null && !headingsLookups.get(target).equals(headingsLookups.get(source))) {
                    //need to consolidate existing as target
                    for (String existingLookup : headingsLookups.keySet()) {
                        if (headingsLookups.get(existingLookup).equals(headingsLookups.get(source))) {
                            headingsLookups.put(existingLookup, headingsLookups.get(target));
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

    private static void extendList(Workbook ppBook, Map<String, String> sourceList, int inputRowNo, boolean matching) {
        List<Name> toBeDeleted = new ArrayList<>();
        for (Name name : ppBook.getAllNames()) {
            try {
                if (name.getNameName().startsWith("az_")) {
                    AreaReference nameArea = new AreaReference(name.getRefersToFormula(), null);
                    if (nameArea.getFirstCell() == nameArea.getLastCell()) {
                        CellReference cellRef = nameArea.getFirstCell();
                        Cell nameCell = makeCell(ppBook.getSheet(cellRef.getSheetName()), cellRef.getRow(), cellRef.getCol());
                        if ((name.getSheetIndex() == 0 && nameCell.getRowIndex() == inputRowNo) || (nameCell.getCellType() == FORMULA && !nameCell.getCellFormula().contains("deleted") && !nameCell.getCellFormula().endsWith(name.getNameName()))) {
                            if (matching) {
                                sourceList.put(name.getNameName(), name.getSheetName());
                            }
                        } else {
                            if (!matching && sourceList.get(name.getNameName()) != null) {
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


    public static List<String> getList(Workbook book, String rangeName) {

        List<String> toReturn = new ArrayList<>();
        try {
            org.apache.poi.ss.usermodel.Name region = BookUtils.getName(book, rangeName);
            if (region == null) return toReturn;

            AreaReference area = new AreaReference(region.getRefersToFormula(), null);
            for (int rowNo = area.getFirstCell().getRow(); rowNo <= area.getLastCell().getRow(); rowNo++) {
                String cellVal = getCellValue(book.getSheet(region.getSheetName()), rowNo, area.getFirstCell().getCol());
                if (cellVal != null) {
                    toReturn.add(cellVal.toLowerCase(Locale.ROOT));
                }
            }
        } catch (Exception e) {
            //ignore at present
        }
        return toReturn;
    }


    public static boolean nameApplies(String possibleName, String toTest) {
        if (possibleName.endsWith("*")) {
            if (possibleName.startsWith("*")) {
                return (toTest.contains(possibleName.substring(1, possibleName.length() - 1)));
            } else {
                return (toTest.startsWith(possibleName.substring(0, possibleName.length() - 1)));
            }
        }
        if (possibleName.startsWith("*")) {
            return toTest.endsWith(possibleName.substring(1));
        }
        return toTest.equals(possibleName);

    }

    public static String getACellValue(Sheet sheet, AreaReference areaRef) {
        return getCellValue(sheet, areaRef.getFirstCell().getRow(), areaRef.getFirstCell().getCol());
    }

    public static String getCellValue(Sheet sheet, int row, int col) {
        try {
            Cell cell = sheet.getRow(row).getCell(col);
            try {
                return cell.getStringCellValue();
            } catch (Exception e) {

                if (cell.getCellStyle().getDataFormatString().contains("mm")) {
                    Long l = ((long) cell.getNumericCellValue() - 25569) * 86400000;
                    LocalDateTime dt = LocalDateTime.ofInstant(Instant.ofEpochMilli(l),
                            TimeZone.getDefault().toZoneId());
                    return DateTimeFormatter.ofPattern("yyyy-MM-dd").format(dt);
                } else {
                    String st = cell.getNumericCellValue() + "";
                    if (st.endsWith(".0")) {
                        return st.substring(0, st.length() - 2);
                    }
                    return st;
                }
            }
        } catch (Exception e) {
            return "";
        }
    }


    private static Cell makeCell(Sheet sheet, int row, int col) {
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
        Cell targetCell = makeCell(sheet, row, col);
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

    //building up a list of mapping regions from xxx_persist to xxx (for values that only occur sporadically)
    public static Map<org.apache.poi.ss.usermodel.Name, AreaReference> getPersistNames
    (org.apache.poi.ss.usermodel.Workbook book){
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

    private static void cleanNames (Workbook ppBook){
        List<Name> toBeDeleted = new ArrayList<>();
        for (Name name : ppBook.getAllNames()) {
            try {
                String ar = name.getRefersToFormula();

                if (name.getSheetName().startsWith("deleted")) {
                    toBeDeleted.add(name);
                }
                try {
                    //and zap the formula if it refers to a deleted sheet.
                    CellReference nameCellRef = new AreaReference(ar, null).getFirstCell();
                    Cell nameCell = ppBook.getSheet(name.getSheetName()).getRow(nameCellRef.getRow()).getCell(nameCellRef.getCol());
                    if (nameCell.getCellType().equals(FORMULA) && nameCell.getCellFormula().contains("deleted")) {
                        nameCell.removeFormula();
                    }
                } catch (Exception e) {
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


    private static int findFirst(Map<Integer, String> map, String toFind) {
        for (int i : map.keySet()) {
            if (map.get(i).equals(toFind)) {
                return i;
            }
        }
        return -1;
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



    private static void clearRow(Row row) {
        if (row==null) {
            return;
        }
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
            ImportService.readPreparedFile(loggedInUser, uploadedFile, false, null, templateCache);

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
                        List<OutputFile> outputFiles = Preprocessor.preProcessUsingPoi(loggedInUser, uf, preprocessorTempLocation.getPath());
                        addToZip(outputFiles,zipforuploadresult);
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
                                    final String cellValue = getCellValue(sheet, cell.getRowIndex(), cell.getColumnIndex());//this is slightly odd
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
                                List<OutputFile> outputFiles = Preprocessor.preProcessUsingPoi(loggedInUser, uf, preprocessorTempLocation.getPath());
                                addToZip(outputFiles, zipforuploadresult);

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

    private static void addToZip(List<OutputFile> outputFiles, Path zipforuploadresult)throws Exception{
        for (OutputFile outputFile:outputFiles) {
            String name = outputFile.outFile.getPath();
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
            Files.copy(Paths.get(outputFile.outFile.getPath()), zipforuploadresult.resolve(name));
        }
    }

}
