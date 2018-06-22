package com.azquo.dataimport;

import com.azquo.ThreadPools;
import com.azquo.memorydb.AzquoMemoryDBConnection;
import com.azquo.memorydb.DatabaseAccessToken;
import com.azquo.memorydb.core.Name;
import com.azquo.memorydb.service.NameService;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvParser;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import groovy.lang.GroovyRuntimeException;
import groovy.lang.GroovyShell;
import groovy.lang.Script;
import org.springframework.util.StringUtils;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
 * <p>
 * Created by Edd on 20/05/15.
 * <p>
 * This class is the entry point for processing an uploaded data file. It will do basic preparation e.g. guessing the number of
 * lines and possible headings that may be stored in the database. Simple sets files are dealt with in here.
 * Otherwise other classes in this package are required to parse headers and read the lines.
 * <p>
 * Azquo has no schema like an SQL database but to load data a basic set structure needs to be defined
 * and rules for interpreting files need to be also. These two together effectively are the equivalent of an SQL schema.
 * <p>
 * The cell on a line can be a value or an attribute or a name - or a part of another cell via composite.
 * <p>
 */
public class DSImportService {
    // Attribute names for a name in the database with importing instructions.
    // to do with reading the file in the first place, are the headings already in the DB as an attribute?
    private static final String HEADINGSSTRING = "HEADINGS";
    // how many lines to skip before data?
    private static final String SKIPLINESSTRING = "SKIPLINES";
    // override default encoding
    private static final String FILEENCODING = "FILEENCODING";
    // is there a groovy pre processor?
    private static final String GROOVYPROCESSOR = "GROOVYPROCESSOR";

    // where we store import specs for different file types
    public static final String ALLIMPORTSHEETS = "All import sheets";
    /*
    Currently only two types of import supported and detection on file name (best idea?). Run the import and persist.
    Generally speaking creating the import headers and basic set structure is what is required to ready a database to load data.

    An entry point to the class functionality.
    */

    public static String readPreparedFile(DatabaseAccessToken databaseAccessToken, String filePath, String fileName, String zipName, List<String> languages, String user, boolean persistAfter, boolean isSpreadsheet) throws Exception {
        System.out.println("Reading file " + filePath);
        AzquoMemoryDBConnection azquoMemoryDBConnection = AzquoMemoryDBConnection.getConnectionFromAccessToken(databaseAccessToken);
        // in an ad hoc spreadsheet area should it say imported? Hard to detect at this point. isSpreadsheet means it could be an XLSX import, a different thing from a data entry area.
        azquoMemoryDBConnection.setProvenance(user, "imported", fileName, "");
        if (fileName.contains(":")) {
            fileName = fileName.substring(fileName.indexOf(":") + 1);//remove the workbook name.  sent only for the provenance.
        }
        return readPreparedFile(azquoMemoryDBConnection, filePath, fileName, zipName,languages, persistAfter, isSpreadsheet, new AtomicInteger());
    }

    // Other entry point into the class functionality, called by above but also directly from DSSpreadsheet service when it has prepared a CSV from data entered ad-hoc into a sheet
    // I wonder if the valuesModifiedCounter is a bit hacky, will maybe revisit this later
    public static String readPreparedFile(AzquoMemoryDBConnection azquoMemoryDBConnection, String filePath, String fileName, String zipName, List<String> languages, boolean persistAfter, boolean isSpreadsheet, AtomicInteger valuesModifiedCounter) throws Exception {
        // ok the thing he is to check if the memory db object lock is free, more specifically don't start an import if persisting is going on, since persisting never calls import there should be no chance of a deadlock from this
        // of course this doesn't currently stop the opposite, a persist being started while an import is going on.
        azquoMemoryDBConnection.lockTest();
        azquoMemoryDBConnection.getAzquoMemoryDB().clearCaches();
        String toReturn;
        if (fileName.toLowerCase().startsWith("sets")) { // typically from a sheet with that name in a book
            // not currently paying attention to isSpreadsheet - only possible issue is the replacing of \\\n with \n required based off writeCell in ImportFileUtilities
            toReturn = setsImport(azquoMemoryDBConnection, filePath, fileName, languages);
        } else {
            toReturn = valuesImport(azquoMemoryDBConnection, filePath, fileName, zipName, languages, isSpreadsheet, valuesModifiedCounter);
            //now look to see if there's a need to execute after import
            Name importInterpreter = findInterpreter(azquoMemoryDBConnection, fileName, languages);
            if (importInterpreter != null) {
                String execute = importInterpreter.getAttribute("EXECUTE");
                if (execute != null && execute.length() > 0) {
                    toReturn += "EXECUTE:" + execute + "EXECUTEEND";
                }

            }
        }
        if (persistAfter) { // get back to the user straight away. Should not be a problem, multiple persists would be queued. The only issue is of changes while persisting, need to check this in the memory db.
            new Thread(azquoMemoryDBConnection::persist).start();
        }
        return toReturn;
    }

    // typically used to create the basic name structure, an Excel set up workbook with many sheets would have a sets sheet

    private static String setsImport(final AzquoMemoryDBConnection azquoMemoryDBConnection, final String filePath, String fileName, List<String> languages) throws Exception {
        int lines;
        try (BufferedReader br = Files.newBufferedReader(Paths.get(filePath))) {
            // the filename can override the attribute for name creation/search. Seems a bit hacky but can make sense if the set up is a series of workbooks.
            if (fileName.length() > 4 && fileName.charAt(4) == '-') { // see if you can derive a language from the file name
                String sheetLanguage = fileName.substring(5);
                if (sheetLanguage.contains(".")) { // knock off the suffix if it's there. Used to be removed client side, makes more sense here
                    sheetLanguage = sheetLanguage.substring(0, sheetLanguage.lastIndexOf("."));
                }
                languages = Collections.singletonList(sheetLanguage);
            }
            String line;
            lines = 0;
            // should we be using a CSV reader?
            while ((line = br.readLine()) != null) {
                String[] cells = line.split("\t"); // split does NOT return empty cells so to speak but it might return "" or a blank space
                Name set = null;
                for (String cell : cells) {
                    cell = cell.replace("\"", "").trim();
                    if (!cell.isEmpty()) { // why we're not just grabbing the first cell for the set, might be blank
                        if (set == null) { // assign it - I'm not just grabbing the first cell since there may be cells with spaces or "" at the beginning
                            set = NameService.findOrCreateNameInParent(azquoMemoryDBConnection, cell, null, false, languages);
                            // empty it in case it existed and had children
                            set.setChildrenWillBePersisted(Collections.emptyList());
                        } else { // set is created or found, so start gathering children
                            set.addChildWillBePersisted(NameService.findOrCreateNameInParent(azquoMemoryDBConnection, cell, set, false, languages));
                        }
                    }
                }
            }
            lines++;
        }
        return fileName + " imported. " + lines + " line(s) of a set file.";  // HTML in DB code! Will let it slide for the mo.
    }

    /* Calls header validation and batches up the data with headers ready for batch importing. Get headings first,
    they can be in a name or in the file, if in a file then they will be set on a name after for future reference.
    The key is to set up info in names so a file can be uploaded from a client "as is". Function a little longer than I'd like.
    */

    private static String valuesImport(final AzquoMemoryDBConnection azquoMemoryDBConnection, String filePath, String fileName, String zipName, List<String> origLanguages, boolean isSpreadsheet, AtomicInteger valuesModifiedCounter) throws Exception {
        try {
            long track = System.currentTimeMillis();
            // A fair amount is going on in here checking various upload options and parsing the headers. It delivers the
            // parsed headings, the iterator for the data lines and how many lines should be processed by each task in the thread pool
            // if there is a text file imported, there may still only be one language when there should be two....but the name of the language will be in fileName

            List<String> languages = new ArrayList<>();
            if (zipName != null && origLanguages.size()==1) {
                languages.add(fileName.substring(0, fileName.indexOf(" ")));
            }
            languages.addAll(origLanguages);
            HeadingsWithIteratorAndBatchSize headingsWithIteratorAndBatchSize = getHeadersWithIteratorAndBatchSize(azquoMemoryDBConnection, fileName, zipName, isSpreadsheet, filePath, languages);
            if (headingsWithIteratorAndBatchSize == null) {
                return fileName + " No data that can be read"; //most likely cause of it being null
            }
            // now, since this will be multi threaded need to make line objects to batch up. Cannot be completely immutable due to the current logic e.g. composite values
            ArrayList<List<ImportCellWithHeading>> linesBatched = new ArrayList<>(headingsWithIteratorAndBatchSize.batchSize);
            List<Future> futureBatches = new ArrayList<>();
            // Local cache of names just to speed things up, a name could be referenced millions of times in one file
            final Map<String, Name> namesFoundCache = new ConcurrentHashMap<>();
            final Set<Integer> linesRejected = Collections.newSetFromMap(new ConcurrentHashMap<>(0)); // track line numbers rejected
            int lineNo = 1; // for error checking etc. Generally the first line of data is line 2, this is incremented at the beginning of the loop. Might be inaccurate if there are vertically stacked headers.
            while (headingsWithIteratorAndBatchSize.lineIterator.hasNext()) { // the main line reading loop
                String[] lineValues = headingsWithIteratorAndBatchSize.lineIterator.next();
                lineNo++;
                List<ImportCellWithHeading> importCellsWithHeading = new ArrayList<>();
                int columnIndex = 0;
                boolean corrupt = false;
                boolean blankLine = true;
                for (ImmutableImportHeading immutableImportHeading : headingsWithIteratorAndBatchSize.headings) {
                    // Intern may save a little memory if strings are repeated a lot. Column Index could point past line values for things like composite.
                    String lineValue = columnIndex < lineValues.length ? lineValues[columnIndex].trim().intern() : "";
                    if (lineValue.length() > 0) blankLine = false;
                    if (lineValue.equals("\"")) {// was a problem, might be worth checking if it is still
                        corrupt = true;
                        break;
                    }
                    if (lineValue.startsWith("\"") && lineValue.endsWith("\""))
                        lineValue = lineValue.substring(1, lineValue.length() - 1).replace("\"\"", "\"");//strip spurious quote marks inserted by Excel
                    // if generated from a spreadsheet this is a danger, fix now before any interpreting
                    //.replace("\n", "\\\\n").replace("\t", "\\\\t") is what that function did on the report server.
                    if (isSpreadsheet) {
                        lineValue = lineValue.replace("\\\\t", "\t").replace("\\\\n", "\n");
                    }
                    importCellsWithHeading.add(new ImportCellWithHeading(immutableImportHeading, lineValue));
                    columnIndex++;
                }
                if (!corrupt && !blankLine) {
                    linesBatched.add(importCellsWithHeading);
                    // Start processing this batch. As the file is read the active threads will rack up to the maximum number allowed rather than starting at max. Store the futures to confirm all are done after all lines are read.
                    if (linesBatched.size() == headingsWithIteratorAndBatchSize.batchSize) {
                        futureBatches.add(ThreadPools.getMainThreadPool().submit(new BatchImporter(azquoMemoryDBConnection, valuesModifiedCounter, linesBatched, namesFoundCache, languages, lineNo - headingsWithIteratorAndBatchSize.batchSize, linesRejected)));// line no should be the start
                        linesBatched = new ArrayList<>(headingsWithIteratorAndBatchSize.batchSize);
                    }
                }else{
                    lineNo--;
                }
            }
            // load leftovers
            int loadLine = lineNo - linesBatched.size(); // NOT batch size! A problem here isn't a functional problem but it makes logging incorrect.
            futureBatches.add(ThreadPools.getMainThreadPool().submit(new BatchImporter(azquoMemoryDBConnection, valuesModifiedCounter, linesBatched, namesFoundCache, languages, loadLine, linesRejected)));
            // check all work is done and memory is in sync
            for (Future<?> futureBatch : futureBatches) {
                futureBatch.get(1, TimeUnit.HOURS);
            }
            // wasn't closing before, maybe why the files stayed there. Use the original one to close in case the lineIterator field was reassigned by transpose
            headingsWithIteratorAndBatchSize.originalIterator.close();
            // Delete check for tomcat temp files, if read from the other temp directly then leave it alone
            if (filePath.contains("/usr/")) {
                File test = new File(filePath);
                if (test.exists()) {
                    if (!test.delete()) {
                        System.out.println("unable to delete " + filePath);
                    }
                }
            }
            StringBuilder toReturn = new StringBuilder();
            toReturn.append(fileName);
            toReturn.append(" imported. Dataimport took ")// I'm not sure I agree with intellij warning about non chained
                    .append((System.currentTimeMillis() - track) / 1000)
                    .append(" second(s) for ")
                    .append(lineNo - 1)
                    .append(" lines").append(", ").append(valuesModifiedCounter).append(" values adjusted");

            // add a bit of feedback for rejected lines. Factor? It's not complex stuff.
            if (!linesRejected.isEmpty()) {
                toReturn.append(" - No. lines rejected: " + linesRejected.size() + " - Line numbers with rejected cells : ");
                int col = 0;
                ArrayList<Integer> lineNumbersList = new ArrayList<>(linesRejected);
                Collections.sort(lineNumbersList); // should do the basic sort
                for (int line : lineNumbersList) {
                    if (col > 0) {
                        toReturn.append(", ");
                    }
                    toReturn.append(line);
                    col++;
                    if (col == 20) {
                        col = 0;
                        //toReturn.append("<br/>\n");
                    }
                }
                if (linesRejected.size() == 1_000) { // it was full - factor the size?
                    toReturn.append("etc.");
                }
                //toReturn.append("<br/>\n");
            }
            azquoMemoryDBConnection.addToUserLogNoException(toReturn.toString(), true);
            System.out.println("---------- names found cache size " + namesFoundCache.size());
            return toReturn.toString();
        } catch (Exception e) {
            // the point of this is to add the file name to the exception message - I wonder if I should just leave a vanilla exception here and deal with this client side?
            e.printStackTrace();
            Throwable t = e;
            if (t.getCause() != null) { // once should do it, unwrap to reduce java.lang.exception being shown to the user
                t = t.getCause();
            }
            //throw new Exception(fileName + " : " + t.getMessage());
            return "Import error: " + t.getMessage();
        }
    }

    // little class to gather the objects required to start the values import

    private static class HeadingsWithIteratorAndBatchSize {
        MappingIterator<String[]> originalIterator;
        Iterator<String[]> lineIterator;
        int batchSize;
        List<ImmutableImportHeading> headings;

        HeadingsWithIteratorAndBatchSize(MappingIterator<String[]> originalIterator, int batchSize) {
            this.originalIterator = originalIterator;
            this.batchSize = batchSize;
            this.lineIterator = originalIterator; // initially use the original, it may be overwritten
        }
    }

    // The function to make the an instance of HeadingsWithIteratorAndBatchSize. It will itself call a few utility functions as well as the HeadingReader class.
    // This function is mainly concerned with possible sources of the headers either in the database as attributes or in the uploaded file

    public static Name findInterpreter(AzquoMemoryDBConnection azquoMemoryDBConnection, String importInterpreterLookup, List<String> languages) throws Exception {
        if (importInterpreterLookup.contains(".")) {
            importInterpreterLookup = importInterpreterLookup.substring(0, importInterpreterLookup.lastIndexOf("."));
        }
        // this distinction was previously dealt with by fileType and fileName, we still support the filename being replaced
        // in the headings and it expects this (filename without extension), importInterpreterLookup is no good as it may be mangled further
        // try to find a name which might have the headings in its attributes
        Name importInterpreter = NameService.findByName(azquoMemoryDBConnection, "dataimport " + importInterpreterLookup, languages);
        //we can use the import interpreter to import different files by suffixing the name with _ or a space and suffix.
        while (importInterpreter == null && (importInterpreterLookup.contains(" ") || importInterpreterLookup.contains("_"))) {
            //There may, though, be separate interpreters for A_B_xxx and A_xxx, so we try A_B first
            if (importInterpreterLookup.contains(" ")) {
                importInterpreterLookup = importInterpreterLookup.substring(0, importInterpreterLookup.lastIndexOf(" "));
            } else {
                importInterpreterLookup = importInterpreterLookup.substring(0, importInterpreterLookup.lastIndexOf("_"));
            }
            importInterpreter = NameService.findByName(azquoMemoryDBConnection, "dataimport " + importInterpreterLookup, languages);
        }
        return importInterpreter;

    }

    private static HeadingsWithIteratorAndBatchSize getHeadersWithIteratorAndBatchSize(AzquoMemoryDBConnection azquoMemoryDBConnection
            , String importInterpreterLookup, String zipName, boolean isSpreadsheet, String filePath, List<String> languages) throws Exception {
        String fileNameForReplace = importInterpreterLookup;

        String importAttribute = null;
        if (zipName!=null) {
            importAttribute = "HEADINGS " + zipName;
        }
        Name importInterpreter = null;
        if (zipName != null){
            importInterpreter = NameService.findByName(azquoMemoryDBConnection,"dataimport " + zipName);
        }
        if (importInterpreter == null){
            importInterpreter = findInterpreter(azquoMemoryDBConnection, importInterpreterLookup, languages);
         }
        // check if that name (assuming it's not null!) has groovy in an attribute
        filePath = checkGroovy(azquoMemoryDBConnection, filePath, importInterpreter);
        if (importInterpreter != null && !maybeHasHeadings(filePath))
            isSpreadsheet = false;//allow data uploaded in a spreadsheet to use pre-configured headings
        // checks the first few lines to sort batch size and get a hopefully correctly configured line iterator
        final HeadingsWithIteratorAndBatchSize lineIteratorAndBatchSize = getLineIteratorAndBatchSize(filePath, importInterpreter); // created here but it has no headers
        if (lineIteratorAndBatchSize == null) {
            return null;
        }
        List<String> headers = new ArrayList<>();
        int skipLines = 0;
        if (!isSpreadsheet && importInterpreter != null) {
            // transpose the file and reassign the line iterator if required, this was for spark response, we'll leave it in for the moment
            // The code below should be none the wiser that a transpose happened if it did.
            checkTranspose(importInterpreter, lineIteratorAndBatchSize);
            String importHeaders = importInterpreter.getAttribute(HEADINGSSTRING); // look for headers and parse them if they are there
            if (importHeaders != null) {
                String skipLinesSt = importInterpreter.getAttribute(SKIPLINESSTRING);
                if (skipLinesSt != null) {
                    try {
                        skipLines = Integer.parseInt(skipLinesSt);
                    } catch (Exception ignored) {
                    }
                }
                System.out.println("has headers " + importHeaders);
                headers = Arrays.asList(importHeaders.split("¬")); // delimiter a bit arbitrary, would like a better solution if I can think of one.
            }
        }
        int headingLineCount = 1;
        Set<Name> topHeadingNames = new HashSet<>();
        Map<String,String> topHeadings = new HashMap<>();
        if (importInterpreter != null && importInterpreter.hasChildren()){//check for top headers
            //CHECK FOR CONVERSION :   PERMISSIBLE IN '.<language name> is <header name> additional header info
            // this converts to header name in this attribute (without brackets), together with additional header info in the attribute (importInterpreter.getDefaultDisplayName() + " " + <language>
            checkImportChildrenForConversion(importInterpreter, languages.get(0));
            for (Name name:importInterpreter.getChildren()){

                String interpretation = name.getAttribute(importAttribute);
                String languageName = name.getAttribute(languages.get(0));
                if (languages.size() == 2) {
                    if (languageName != null) {
                        int thisHeadingLineCount = StringUtils.countOccurrencesOf(languageName, "|");

                        if (thisHeadingLineCount + 1 > headingLineCount) {
                            headingLineCount = thisHeadingLineCount + 1;
                        }
                    }
                    String localInterpretation = name.getAttribute(importAttribute + " " + languages.get(0));
                    if (localInterpretation != null) {
                        if (interpretation==null){
                            interpretation = localInterpretation;
                        }
                        interpretation += ";" + localInterpretation;
                    }
                }
                if (interpretation!= null && interpretation.toLowerCase().contains(HeadingReader.TOPHEADING)){
                    topHeadingNames.add(name);
                 }
            }
            int lineNo = 0;
            while(topHeadingNames.size() > 0 && lineNo < 20 && lineIteratorAndBatchSize.lineIterator.hasNext()){
                String lastHeading = null;
                headers = getNextLine(lineIteratorAndBatchSize);
                for (String header:headers){
                    if (lastHeading!=null){
                        Name headingName = NameService.findByName(azquoMemoryDBConnection, lastHeading, languages);
                        if (headingName!=null && topHeadingNames.contains(headingName)){
                            MutableImportHeading newMHeading = new MutableImportHeading();
                            topHeadings.put(lastHeading, header);

                            topHeadingNames.remove(headingName);
                        }
                    }
                    lastHeading = header;
                }
                lineNo++;
            }
            if (lineNo++ < 20 && lineIteratorAndBatchSize.lineIterator.hasNext()){
                headers = getNextLine(lineIteratorAndBatchSize);

            }
            while (lineNo < 20 && (headers.size()==0 || headers.get(0).length()== 0)&& lineIteratorAndBatchSize.lineIterator.hasNext()){ //looking for something in column A
                headers = getNextLine(lineIteratorAndBatchSize);

            }
            if (headingLineCount >1){
                buildHeadersFromVerticallyListedNames(headers, lineIteratorAndBatchSize.lineIterator, headingLineCount -1);

            }

            if (lineNo==20 || !lineIteratorAndBatchSize.lineIterator.hasNext()){
                return  null;//TODO   notify that headings are not found.
            }
        }
        // We might use the headers on the data file, this is notably used when setting up the headers themselves.
        if (headers.size() == 0) {
            headers = getNextLine(lineIteratorAndBatchSize);
            for (int i = 0; i < headers.size(); i++) {
                // might have gotten in there if generating a csv from an Excel sheet, writeCell from ImportFileUtilities, undo it! Since this is just for headers no need to check based off a flag.
                //.replace("\n", "\\\\n").replace("\t", "\\\\t")
                headers.set(i, headers.get(i).replace("\\\\n", "\n").replace("\\\\t", "\t"));
            }
            boolean hasClauses = false;
            for (String header : headers) {
                if (header.contains(".") || header.contains(";")) {
                    hasClauses = true;
                }
            }
            if (!hasClauses) {
                if (!lineIteratorAndBatchSize.lineIterator.hasNext()) {
                    throw new Exception("Invalid headers on import file - is this a report that required az_ReportName?");
                }
                // option to stack the clauses vertically
                List<String> oldHeaders = new ArrayList<>(headers);
                if (!buildHeadersFromVerticallyListedClauses(headers, lineIteratorAndBatchSize.lineIterator)) {
                    headers = oldHeaders;
                }
            }
            if (isSpreadsheet && zipName == null) { // it's saying really is it a template (isSpreadsheet = yes)
                // basically if there were no headings in the DB but they were found in the file then put them in the DB to be used by files with the similar names
                // as in add the headings to the first upload then upload again without headings (assuming the file name is the same!)
                Name importSheets = NameService.findOrCreateNameInParent(azquoMemoryDBConnection, ALLIMPORTSHEETS, null, false);
                Name dataImportThis = NameService.findOrCreateNameInParent(azquoMemoryDBConnection,
                        "DataImport " + importInterpreterLookup, importSheets, true);
                StringBuilder sb = new StringBuilder();
                for (String header : headers) {
                    sb.append(header).append("¬");
                }
                dataImportThis.setAttributeWillBePersisted(HEADINGSSTRING, sb.toString());
                dataImportThis.setAttributeWillBePersisted(SKIPLINESSTRING, "1");//  currently assuming one line - may need to adjust
            }
        } else {
            while (skipLines-- > 0) {
                lineIteratorAndBatchSize.lineIterator.next();
            }
        }
        int topHeadingPos = headers.size();
        if (topHeadings!=null){

            headers.addAll(topHeadings.keySet());
        }
        checkRequiredHeadings(azquoMemoryDBConnection,headers,importInterpreter, languages);
        // internally can further adjust the headings based off a name attributes. See HeadingReader for details.
        headers = HeadingReader.preProcessHeadersAndCreatePivotSetsIfRequired(azquoMemoryDBConnection, headers,  importInterpreter, fileNameForReplace, languages);//attribute names may have additions when language is in the context
        if (topHeadings !=null){
            for (String topHeading:topHeadings.keySet()){
                headers.set(topHeadingPos, headers.get(topHeadingPos++) + ";default " + topHeadings.get(topHeading));
            }
        }
         lineIteratorAndBatchSize.headings = HeadingReader.readHeaders(azquoMemoryDBConnection, headers, languages);
        return lineIteratorAndBatchSize;
    }

    private static List<String>getNextLine(HeadingsWithIteratorAndBatchSize lineIterator){
        List<String> toReturn = new ArrayList<>();
        toReturn.addAll(Arrays.asList(lineIterator.lineIterator.next()));
        return toReturn;
    }
    private static void checkRequiredHeadings(AzquoMemoryDBConnection azquoMemoryDBConnection, List<String> headers, Name importInterpreter, List<String> languages) throws Exception {
        if (importInterpreter==null || !importInterpreter.hasChildren())  return;

        List<String> defaultNames = new ArrayList<>();
        for (String header : headers) {
            Name name = NameService.findByName(azquoMemoryDBConnection, header, languages);
            if (name != null) {
                defaultNames.add(name.getDefaultDisplayName());
            } else
                defaultNames.add(header);
        }
        String importAttribute = importInterpreter.getDefaultDisplayName().replace("DATAIMPORT", "HEADINGS");
        for (Name name : importInterpreter.getChildren()) {
            String attribute = NameService.getCompositeAttributes(name,importAttribute,importAttribute + " " + languages.get(0));

            if (attribute != null) {
                boolean required = false;
                boolean composition = false;
                String[] clauses = attribute.split(";");
                for (String clause : clauses) {
                    if (clause.toLowerCase().startsWith("required")) {
                        required = true;
                    }
                    if (clause.toLowerCase().startsWith("composition")) {
                        composition = true;
                    }
                    if (!defaultNames.contains(name.getDefaultDisplayName()) && required) {
                        if ( composition) {
                            headers.add(name.getDefaultDisplayName());
                            defaultNames.add(name.getDefaultDisplayName());
                        }else{
                            //check both the general and specific import attributes
                            String attribute2 = NameService.getCompositeAttributes(name, importAttribute, importAttribute + " " + languages.get(0));
                            if (attribute2 == null || (!attribute2.toLowerCase().contains("default") && !attribute2.toLowerCase().contains("composition"))) {
                                throw new Exception("headers missing required header: " + name.getDefaultDisplayName());
                            } else {
                                headers.add(name.getDefaultDisplayName());//maybe a problem if there is another name in the given language
                                defaultNames.add(name.getDefaultDisplayName());

                            }
                        }
                    }
                }
            }
        }
    }


    private static boolean maybeHasHeadings(String filePath) throws Exception {
        // checks the first few lines of a spreadsheet file to discover evidence of headings.  ten lines without a blank line, and without clauses in the top line indicates no headings
        try (BufferedReader br = Files.newBufferedReader(Paths.get(filePath), Charset.forName("UTF-8"))) {
            String lineData = br.readLine();
            if (lineData == null || lineData.contains(".") || lineData.contains(";")) {
                br.close();
                return true;
            }
            int lineCount = 10;
            while (lineCount-- > 0) {
                lineData = br.readLine();
                if (lineData == null || lineData.replace("\t", "").replace(" ", "").length() == 0) {
                    br.close();
                    return true;
                }
            }
            br.close();
        }
        return false;
    }

    // the idea is that a header could be followed by successive clauses on cells below and this might be easier to read
    private static boolean buildHeadersFromVerticallyListedClauses(List<String> headers, Iterator<String[]> lineIterator) {
        String[] nextLine = lineIterator.next();
        int headingCount = 1;
        boolean lastfilled;
        while (nextLine != null && headingCount++ < 10) {
            int colNo = 0;
            lastfilled = false;
            // while you find known names, insert them in reverse order with separator |.  Then use ; in the usual order
            for (String heading : nextLine) {
                if (heading.length() > 0 && !heading.equals("--") && colNo < headers.size()) { //ignore "--", can be used to give space below the headers
                    if (heading.startsWith(".")) {
                        headers.set(colNo, headers.get(colNo)+ heading);
                    } else {
                        if (headers.get(colNo).length() == 0) {
                            headers.set(colNo, heading);
                        } else {
                            if (findReservedWord(heading)) {
                                headers.set(colNo, headers.get(colNo) + ";" + heading);
                            } else {
                                headers.set(colNo,  heading + "|" + headers.get(colNo));
                            }
                        }
                    }
                    lastfilled = true;
                }
                colNo++;
            }
            if (lineIterator.hasNext() && lastfilled) {
                nextLine = lineIterator.next();
            } else {
                nextLine = null;
            }
        }
        return headingCount != 11;
    }


    private static boolean buildHeadersFromVerticallyListedNames(List<String> headers, Iterator<String[]> lineIterator, int lineCount) {
        String[] nextLine = lineIterator.next();
        boolean lastfilled;
        while (nextLine != null &&  lineCount-- > 0) {
            int colNo = 0;
            lastfilled = false;
            // while you find known names, insert them in reverse order with separator |.  Then use ; in the usual order
            String lastHeading = null;
            for (String heading : nextLine) {
                if (heading.length() > 0 && !heading.equals("--") && colNo < headers.size()) { //ignore "--", can be used to give space below the headers
                    if (heading.startsWith(".")) {
                        headers.set(colNo, headers.get(colNo) + heading);
                    } else {
                        if (headers.get(colNo).length() == 0) {
                                  int lastSplit = lastHeading.lastIndexOf("|");
                                if (lastSplit > 0){
                                    headers.set(colNo, lastHeading.substring(0,lastSplit + 1) + heading);
                                }else{
                                    headers.set(colNo, lastHeading + "|" + heading);
                                }
                        } else {
                                headers.set(colNo, headers.get(colNo) + "|" + heading);
                         }
                    }
                    lastfilled = true;
                }
                lastHeading = headers.get(colNo);
                colNo++;
            }
            if (lineIterator.hasNext() && lastfilled) {
                nextLine = lineIterator.next();
            } else {
                nextLine = null;
            }
        }
        return lineCount ==  0;
    }



    private static boolean findReservedWord(String heading) {
        heading = heading.toLowerCase();
        return heading.startsWith(HeadingReader.CHILDOF)
                || heading.startsWith(HeadingReader.PARENTOF)
                || heading.startsWith(HeadingReader.ATTRIBUTE)
                || heading.startsWith(HeadingReader.LANGUAGE)
                || heading.startsWith(HeadingReader.PEERS)
                || heading.startsWith(HeadingReader.LOCAL)
                || heading.startsWith(HeadingReader.COMPOSITION)
                || heading.startsWith(HeadingReader.IGNORE)
                || heading.startsWith(HeadingReader.DEFAULT)
                || heading.startsWith(HeadingReader.NONZERO)
                || heading.startsWith(HeadingReader.DATELANG)
                || heading.startsWith(HeadingReader.ONLY)
                || heading.startsWith(HeadingReader.EXCLUSIVE)
                || heading.startsWith(HeadingReader.CLEAR)
                || heading.startsWith(HeadingReader.COMMENT)
                || heading.startsWith(HeadingReader.EXISTING)
                || heading.startsWith(HeadingReader.LINEHEADING)
                || heading.startsWith(HeadingReader.LINEDATA)
                || heading.startsWith(HeadingReader.SPLIT);
    }

    private static void checkTranspose(Name importInterpreter, HeadingsWithIteratorAndBatchSize headingsWithIteratorAndBatchSize) {
        if ("true".equalsIgnoreCase(importInterpreter.getAttribute("transpose"))) {
            final List<String[]> sourceList = new ArrayList<>();
            // originalIterator will be closed at the end. Worth noting that transposing shouldn't really be done on massive files, I can't imagine it would be
            while (headingsWithIteratorAndBatchSize.originalIterator.hasNext()) {
                sourceList.add(headingsWithIteratorAndBatchSize.originalIterator.next());
            }
            final List<String[]> flipped = new ArrayList<>(); // from ths I can get a compatible iterator
            if (!sourceList.isEmpty()) { // there's data to transpose
                final int oldXMax = sourceList.get(0).length; // size of nested list, as described above (that is to say get the length of one row)
                for (int newY = 0; newY < oldXMax; newY++) {
                    String[] newRow = new String[sourceList.size()]; // make a new row
                    int index = 0;
                    for (String[] oldRow : sourceList) { // and step down each of the old rows
                        newRow[index] = oldRow[newY];//so as we're moving across the new row we're moving down the old rows on a fixed column
                        index++;
                    }
                    flipped.add(newRow);
                }
                // replace the line iterator, the values imp[orting code won't notice but either way the original needs to be closed at the end, hence the originalIterator field
                headingsWithIteratorAndBatchSize.lineIterator = flipped.iterator();
            }
        }
    }

    // Getting some simple info about the file to set up the csv reader and determine batch size
    // Makes the HeadingsWithIteratorAndBatchSize but without the ImmutableImportHeadings
    private static HeadingsWithIteratorAndBatchSize getLineIteratorAndBatchSize(String filePath, Name importInterpreter) throws Exception {
        int batchSize = 100_000;
        char delimiter = ',';
        File sizeTest = new File(filePath);
        final long fileLength = sizeTest.length();
         try (BufferedReader br = Files.newBufferedReader(Paths.get(filePath), Charset.forName("UTF-8"))) {
            // grab the first line to check on delimiters
            String firstLine = br.readLine();
            if (firstLine == null || firstLine.length() == 0) {
                br.close();
                return null;
            }
            String secondLine = br.readLine();
            long linesGuess = fileLength / ((secondLine != null && secondLine.length() > 20) ? secondLine.length() : 1_000); // a very rough approximation assuming the second line is a typical length.
            System.out.println("Lines guessed at : " + linesGuess);
            if (linesGuess < 100_000 && fileLength > 1_000_000) {
               batchSize = 5_000;
               System.out.println("less than 100,000, dropping batch size to " + batchSize);

            } else if (linesGuess < 1_000_000 && fileLength > 1_000_000) {
                System.out.println("less than 1,000,000, dropping batch size to 10k");
                batchSize = 10_000;
            }
            if (firstLine.contains("|")) {
                delimiter = '|';
            }
            if (firstLine.contains("\t")) {
                delimiter = '\t';
            }
        }
        // now we know the delimiter can CSV read, I've read jackson is pretty quick
        CsvMapper csvMapper = new CsvMapper();
        csvMapper.enable(CsvParser.Feature.WRAP_AS_ARRAY);
        CsvSchema schema = csvMapper.schemaFor(String[].class)
                .withColumnSeparator(delimiter)
                .withLineSeparator("\n");
        if (delimiter == '\t') {
            schema = schema.withoutQuoteChar();
        }
        /*
        note : for encoding is it worth trying
        https://tika.apache.org/1.2/api/org/apache/tika/detect/AutoDetectReader.html
   //get a file stream in utf format for this file (since they are often not in utf by
   Charset charset = new AutoDetectReader(new FileInputStream(file)).getCharset();
         */
        if (importInterpreter != null && importInterpreter.getAttribute(FILEENCODING) != null) {
            // so override file encoding.
            return new HeadingsWithIteratorAndBatchSize(csvMapper.reader(String[].class).with(schema).readValues(new InputStreamReader(new FileInputStream(filePath), importInterpreter.getAttribute(FILEENCODING))), batchSize);
        } else {
            return new HeadingsWithIteratorAndBatchSize(csvMapper.reader(String[].class).with(schema).readValues(new File(filePath)), batchSize);
        }
    }

    // typically groovy scripts write out to a different file, helps a lot for debugging! Most of the time with no groovy specified will just send back the filePath unchanged.
    private static String checkGroovy(AzquoMemoryDBConnection azquoMemoryDBConnection, String filePath, Name importInterpreter) throws Exception {
        if (importInterpreter != null && importInterpreter.getAttribute(GROOVYPROCESSOR) != null) {
            System.out.println("Groovy found! Running  . . . ");
            Object[] groovyParams = new Object[3];
            groovyParams[0] = filePath;
            groovyParams[1] = azquoMemoryDBConnection;
            GroovyShell shell = new GroovyShell();
            try {
                final Script script = shell.parse(importInterpreter.getAttribute(GROOVYPROCESSOR));
                System.out.println("Groovy done.");
                return (String) script.invokeMethod("fileProcess", groovyParams);
            } catch (GroovyRuntimeException e) {
                // exception could be because the groovy script didn't have a function that matched
                e.printStackTrace();
                throw new Exception("Groovy error " + e.getMessage());
            }
        }
        return filePath;
    }

    private static void checkImportChildrenForConversion(Name importInterpreter,  String language)throws Exception{
        boolean toBeConverted = false;
        for (Name importField:importInterpreter.getChildren()){
            String existingName = importField.getAttribute(language);
            if (existingName!=null && existingName.startsWith("<") && existingName.contains(">")){
                toBeConverted = true;
                break;
            }
        }
        if (toBeConverted){
            String importAttribute = importInterpreter.getDefaultDisplayName().replace("DATAIMPORT", "HEADINGS") + " " + language;
            for (Name importField:importInterpreter.getChildren()){
                String existingName = importField.getAttribute(language);
                if (existingName!=null){
                    String newName = "";
                    String newHeadingAttributes = existingName;
                    if (existingName.startsWith("<")) {
                        int nameEndPos = existingName.indexOf(">");
                        if (nameEndPos > 0) {
                            newName = existingName.substring(1, nameEndPos).trim();
                            newHeadingAttributes = existingName.substring(nameEndPos + 1).trim();
                        }
                    }
                    importField.setAttributeWillBePersisted(language, newName);
                    importField.setAttributeWillBePersisted(importAttribute, newHeadingAttributes);
                }
            }
        }
    }



}