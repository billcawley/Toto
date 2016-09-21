package com.azquo.dataimport;

import com.azquo.memorydb.AzquoMemoryDBConnection;
import com.azquo.memorydb.DatabaseAccessToken;
import com.azquo.memorydb.core.AzquoMemoryDB;
import com.azquo.memorydb.core.Name;
import com.azquo.memorydb.service.NameService;
import com.azquo.spreadsheet.DSSpreadsheetService;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvParser;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import groovy.lang.GroovyRuntimeException;
import groovy.lang.GroovyShell;
import groovy.lang.Script;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
 * <p>
 * Created by cawley on 20/05/15.
 * <p>
 * Has a fair bit of the logic that was in the original import service.
 * Note : large chunks of this were originally written by WFC and then refactored by EFC, spread across new files in this package.
 * <p>
 * Azquo has no schema like an SQL database but to load data a basic set structure needs to be defined
 * and rules for interpreting files need to be also. These two together effectively are the equivalent of an SQL schema.
 * <p>
 * I'm trying to write up as much of the non obvious logic as possible in comments in the code.
 * <p>
 * The cell on a line can be a value or an attribute or a name - or a part of another cell
 */
public class DSImportService {
    // Attribute names for a name in the database with importing instructions.
    // to do with reading the file in the first place, do we read the headers or not, how many lines to skip before data, is there a groovy pre processor
    private static final String HEADINGSSTRING = "HEADINGS";
    private static final String SKIPLINESSTRING = "SKIPLINES";
    private static final String GROOVYPROCESSOR = "GROOVYPROCESSOR";

    /*
    Currently only two types of import supported and detection on file name (best idea?). Run the import and persist.
    Generally speaking creating the import headers and basic set structure is what is required to ready a database to load data.

    An entry point to the class functionality.
    */

    public static String readPreparedFile(DatabaseAccessToken databaseAccessToken, String filePath, String fileName, List<String> attributeNames, String user, boolean persistAfter, boolean isSpreadsheet) throws Exception {
        System.out.println("Reading file " + filePath);
        AzquoMemoryDBConnection azquoMemoryDBConnection = DSSpreadsheetService.getConnectionFromAccessToken(databaseAccessToken);
        // in an ad hoc spreadsheet area should it say imported?
        azquoMemoryDBConnection.setProvenance(user, "imported", fileName, "");
        return readPreparedFile(azquoMemoryDBConnection, filePath, fileName, attributeNames, persistAfter, isSpreadsheet);
    }

    // Other entry point into the class functionality, called by above but also directly from DSSpreadsheet service when it has prepared a CSV from data entered ad-hoc into a sheet

    public static String readPreparedFile(AzquoMemoryDBConnection azquoMemoryDBConnection, String filePath, String fileName, List<String> attributeNames, boolean persistAfter, boolean isSpreadsheet) throws Exception {
        // ok the thing he is to check if the memory db object lock is free, more specifically don't start an import if persisting is going on, since persisting never calls import there should be no chance of a deadlock from this
        // of course this doesn't currently stop the opposite, a persist being started while an import is going on.
        System.out.println("Preparing to import, lock test");
        azquoMemoryDBConnection.lockTest();
        System.out.println("Import lock passed");
        azquoMemoryDBConnection.getAzquoMemoryDB().clearCaches();
        String toReturn;
        if (fileName.toLowerCase().startsWith("sets")) { // typically from a sheet with that name in a book
            toReturn = setsImport(azquoMemoryDBConnection, filePath, fileName, attributeNames);
        } else {
            toReturn = valuesImport(azquoMemoryDBConnection, filePath, fileName, attributeNames, isSpreadsheet);
        }
        if (persistAfter) { // get back to the user straight away. Should not be a problem, multiple persists would be queued. The only issue is of changes while persisting, need to check this in the memory db.
            new Thread(azquoMemoryDBConnection::persist).start();
        }
        return toReturn;
    }

    // typically used to create the basic structure

    private static String setsImport(final AzquoMemoryDBConnection azquoMemoryDBConnection, final String filePath, String fileName, List<String> attributeNames) throws Exception {
        BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(filePath)));
        // the filename can override the attribute for name creation/search. Seems a bit hacky but can make sense if the set up is a series of workbooks.
        if (fileName.length() > 4 && fileName.charAt(4) == '-') { // see if you can derive a language from the file name
            String sheetLanguage = fileName.substring(5);
            if (sheetLanguage.contains(".")) { // knock off the suffix if it's there. Used to be removed client side, makes more sense here
                sheetLanguage = sheetLanguage.substring(0, sheetLanguage.lastIndexOf("."));
            }
            attributeNames = new ArrayList<>();
            attributeNames.add(sheetLanguage);
        }
        String line;
        int lines = 0;
        // should we be using a CSV reader?
        while ((line = br.readLine()) != null) {
            String[] cells = line.split("\t"); // split does NOT return empty cells so to speak but it might return "" or a blank space
            Name set = null;
            for (String cell : cells) {
                cell = cell.replace("\"", "").trim();
                if (!cell.isEmpty()) { // why we're not just grabbing the first cell for the set, might be blank
                    if (set == null) { // assign it - I'm not just grabbing the first cell since there may be cells with spaces or "" at the beginning
                        set = NameService.findOrCreateNameInParent(azquoMemoryDBConnection, cell, null, false, attributeNames);
                        // empty it
                        set.setChildrenWillBePersisted(Collections.emptyList());
                    } else { // set is created or found, so start gathering children
                        set.addChildWillBePersisted(NameService.findOrCreateNameInParent(azquoMemoryDBConnection, cell, set, false, attributeNames));
                    }
                }
            }
        }
        lines++;
        return fileName + " imported. " + lines + " line(s) of a set file.<br/>";  // HTML in DB code! Will let it slide for the mo.
    }

    /* calls header validation and batches up the data with headers ready for batch importing
    Get headings first, they can be in a name or in the file, if in a file then they will be set on a name. The key is to set up info in names so a file can be uploaded from a client "as is"
    */

    private static String valuesImport(final AzquoMemoryDBConnection azquoMemoryDBConnection, String filePath, String fileName, List<String> attributeNames, boolean isSpreadsheet) throws Exception {
        try {
            long track = System.currentTimeMillis();
            // I'd like to break assigning the import interpreter off but it's tied rather closely with code below
            String importInterpreterLookup = fileName; // breaking this off as a new field - used to find names in the db with info to interpret the file
            if (importInterpreterLookup.contains(".")) {
                importInterpreterLookup = importInterpreterLookup.substring(0, importInterpreterLookup.lastIndexOf("."));
            }
            Name importInterpreter = NameService.findByName(azquoMemoryDBConnection, "dataimport " + importInterpreterLookup, attributeNames);
            while (!isSpreadsheet && importInterpreter == null && (importInterpreterLookup.contains(" ") || importInterpreterLookup.contains("_"))) { //we can use the import interpreter to import different files by suffixing the name with _ or a space and suffix.
                //There may, though, be separate interpreters for A_B_xxx and A_xxx, so we try A_B first
                if (importInterpreterLookup.contains(" ")) {
                    importInterpreterLookup = importInterpreterLookup.substring(0, importInterpreterLookup.lastIndexOf(" "));
                } else {
                    importInterpreterLookup = importInterpreterLookup.substring(0, importInterpreterLookup.lastIndexOf("_"));
                }
                importInterpreter = NameService.findByName(azquoMemoryDBConnection, "dataimport " + importInterpreterLookup, attributeNames);
            }
            filePath = checkGroovy(azquoMemoryDBConnection, filePath, importInterpreter);
            // keep this one separate so it can be closed at the end. Transposing may assign a new iterator
            final LineIteratorAndBatchSize lineIteratorAndBatchSize = getLineIteratorAndBatchSize(filePath);
            if (lineIteratorAndBatchSize == null) {
                return "First line blank"; //if he first line is blank, ignore the sheet
            }
            int batchSize = lineIteratorAndBatchSize.batchSize;
            Iterator<String[]> lineIterator = lineIteratorAndBatchSize.lineIterator; // to read the data, it might be reassigned in the case of transposing which will return the more generic iterator
            String[] headers = null;
            int skipLines = 0;
            if (!isSpreadsheet && importInterpreter != null) {
                lineIterator = checkTranspose(importInterpreter, lineIterator); // transpose the file if required, this was for spark response, we'll leave it in for the moment
                // The code below should be none the wiser that a transpose happened if it did.
                String importHeaders = importInterpreter.getAttribute(HEADINGSSTRING); // typically they will be in there
                if (importHeaders != null) {
                    String skipLinesSt = importInterpreter.getAttribute(SKIPLINESSTRING);
                    if (skipLinesSt != null) {
                        try {
                            skipLines = Integer.parseInt(skipLinesSt);
                        } catch (Exception ignored) {
                        }
                    }
                    System.out.println("has headers " + importHeaders);
                    headers = importHeaders.split("¬"); // a bit arbitrary, would like a better solution if I can think of one.
                }
            }

            // finally we might use the headers on the data file, this is notably used when setting up the headers themselves :)
            if (headers == null) {
                headers = lineIterator.next();
                boolean hasClauses = false;
                for (String header : headers) {
                    if (header.contains(".") || header.contains(";")) {
                        hasClauses = true;
                    }
                }
                if (!hasClauses) {
                    if (!lineIterator.hasNext()) {
                        throw new Exception("Invalid headers on import file - is this a report that required az_ReportName?");
                    }
                    buildHeadersFromVerticallyListedClauses(headers, lineIterator);
                }
                if (isSpreadsheet) { // it's saying really is it a template (isSpreadsheet = yes)
                    Name importSheets = NameService.findOrCreateNameInParent(azquoMemoryDBConnection, "All import sheets", null, false);
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
                    lineIterator.next();
                }
            }

        /*
        End preparatory stuff
        readHeaders is about creating a set of ImportHeadings
        notable that internally it might use attributes from the relevant data import name to supplement the header information
        to be more specific : that name called by "dataimport " + fileType has been hit for its "HEADINGSSTRING" attribute already to produce headers
        but it could be asked for something more specific according to the header name.
        This method where columns can be called by name will look nicer in the heading set up but it requires data files to have headings.
        */
            // read the clauses, assign the heading.name if you can find it, add on the context headings
            final List<ImmutableImportHeading> immutableImportHeadings = HeadingReader.readHeaders(azquoMemoryDBConnection, headers, importInterpreterLookup, attributeNames);
            // having read the headers go through each record
            // now, since this will be multi threaded need to make line objects, Cannot be completely immutable due to the current logic e.g. composite values
            int lineNo = 1; // for error checking etc. Generally the first line of data is line 2, this is incremented at the beginning of the loop
            // pretty vanilla multi threading bits
            AtomicInteger valueTracker = new AtomicInteger(0);
            ArrayList<List<ImportCellWithHeading>> linesBatched = new ArrayList<>(batchSize);
            int colCount = immutableImportHeadings.size();
            while (immutableImportHeadings.get(colCount - 1).compositionPattern != null)
                colCount--;
            List<Future> futureBatches = new ArrayList<>();
            // Local cache of names just to speed things up, a name could be referenced millions of times in one file
            final Map<String, Name> namesFoundCache = new ConcurrentHashMap<>();
            while (lineIterator.hasNext()) {
                String[] lineValues = lineIterator.next();
                lineNo++;
                List<ImportCellWithHeading> importCellsWithHeading = new ArrayList<>();
                int columnIndex = 0;
                boolean corrupt = false;
                for (ImmutableImportHeading immutableImportHeading : immutableImportHeadings) {
                    // intern may save a little memory. Column Index could point past line values for things like composite. Possibly other things but I can't think of them at the moment
                    String lineValue = columnIndex < lineValues.length ? lineValues[columnIndex].trim().intern().replace("~~", "\r\n") : "";//hack to replace carriage returns from Excel sheets
                    if (lineValue.equals("\"")) {
                        //this has happened
                        corrupt = true;
                        break;
                    }
                    if (lineValue.startsWith("\"") && lineValue.endsWith("\""))
                        lineValue = lineValue.substring(1, lineValue.length() - 1).replace("\"\"", "\"");//strip spurious quote marks inserted by Excel
                    //remove spurious quotes (put in when Groovyscript sent)
                    importCellsWithHeading.add(new ImportCellWithHeading(immutableImportHeading, lineValue, null));
                    columnIndex++;
                }
                if (!corrupt) {
                    //batch it up!
                    linesBatched.add(importCellsWithHeading);
                    // rack up the futures to check in a mo to see that things are complete
                    if (linesBatched.size() == batchSize) {
                        futureBatches.add(AzquoMemoryDB.mainThreadPool.submit(new BatchImporter(azquoMemoryDBConnection, valueTracker, linesBatched, namesFoundCache, attributeNames, lineNo - batchSize)));// line no should be the start
                        linesBatched = new ArrayList<>(batchSize);
                    }
                }
            }
            // load leftovers
            int loadLine = lineNo - linesBatched.size(); // NOT batch size!
            if (loadLine < 1) loadLine = 1; // could it ever be? Need to confirm this check
            futureBatches.add(AzquoMemoryDB.mainThreadPool.submit(new BatchImporter(azquoMemoryDBConnection, valueTracker, linesBatched, namesFoundCache, attributeNames, loadLine)));// line no should be the start
            // check all work is done and memory is in sync
            for (Future<?> futureBatch : futureBatches) {
                futureBatch.get(1, TimeUnit.HOURS);
            }
            // wasn't closing before, maybe why the files stayed there. Use the original one to close in case the lineIterator field was reassigned by transpose
            lineIteratorAndBatchSize.lineIterator.close();
            // edd adding a delete check for tomcat temp files, if read from the other temp directly then leave it alone
            if (filePath.contains("/usr/")) {
                File test = new File(filePath);
                if (test.exists()) {
                    if (!test.delete()) {
                        System.out.println("unable to delete " + filePath);
                    }
                }
            }
            String toReturn = fileName + " imported. Dataimport took " + (System.currentTimeMillis() - track) / 1000 + " second(s) for " + (lineNo - 1) + " lines<br/>\n";
            azquoMemoryDBConnection.addToUserLogNoException(toReturn, true);
            System.out.println("---------- names found cache size " + namesFoundCache.size());
            return toReturn;
        } catch (Exception e) {
            // the point of this is to add the file name to the exception message - I wonder if I should just leave a vanilla exception here and deal with this client side?
            e.printStackTrace();
            Throwable t = e;
            if (t.getCause() != null) { // once should do it, unwrap to reduce java.lang.exception being shown to the user
                t = t.getCause();
            }
            throw new Exception(fileName + " : " + t.getMessage());
        }
    }

    // the idea is that a header could be followed by successive clauses on cells below and this might be easier to read
    private static void buildHeadersFromVerticallyListedClauses(String[] headers, Iterator<String[]> lineIterator) {
        String[] nextLine = lineIterator.next();
        int headingCount = 1;
        boolean lastfilled = true;
        while (lineIterator.hasNext() && lastfilled && headingCount++ < 10) {
            int colNo = 0;
            lastfilled = false;
            for (String heading : nextLine) {
                if (heading.length() > 2 && colNo < headers.length) { //ignore "--", can be used to give space below the headers
                    if (heading.startsWith(".")) {
                        headers[colNo] += heading;
                    } else {
                        headers[colNo] += ";" + heading;
                    }
                    lastfilled = true;
                }
                colNo++;
            }
            nextLine = lineIterator.next();
        }
    }

    private static Iterator<String[]> checkTranspose(Name importInterpreter, Iterator<String[]> lineIterator) {
        if ("true".equalsIgnoreCase(importInterpreter.getAttribute("transpose"))) {
            // ok we want to transpose, will use similar logic to the server side transpose
            final List<String[]> sourceList = new ArrayList<>();
            while (lineIterator.hasNext()) { // it will be closed at the end. Worth noting that transposing shouldn't really be done on massive files, I can't imagine it would be
                sourceList.add(lineIterator.next());
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
                lineIterator = flipped.iterator(); // replace the iterator, I was keen to keep the pattern the Jackson uses, this seems to support it, the original is closed at the bottom either way
            }
        }
        return lineIterator;// most of the time will just return the unmodified lineIterator
    }

    private static class LineIteratorAndBatchSize {
        final MappingIterator<String[]> lineIterator;
        final int batchSize;

        LineIteratorAndBatchSize(MappingIterator<String[]> lineIterator, int batchSize) {
            this.lineIterator = lineIterator;
            this.batchSize = batchSize;
        }
    }

    // as it says, getting some simple info about the file to set up the csv reader and determine batch size
    private static LineIteratorAndBatchSize getLineIteratorAndBatchSize(String filePath) throws Exception {
        File sizeTest = new File(filePath);
        final long fileLength = sizeTest.length();
        BufferedReader br = new BufferedReader(new FileReader(filePath));
        // grab the first line to check on delimiters
        String firstLine = br.readLine();
        if (firstLine == null) {
            br.close();
            return null;
        }
        String secondLine = br.readLine();
        long linesGuess = fileLength / (secondLine != null ? secondLine.length() : 1_000); // a very rough approximation assuming the second line is a typical length.
        System.out.println("Lines guessed at : " + linesGuess);
        int batchSize = 100_000;
        if (linesGuess < 100_000) {
            System.out.println("less than 100,000, dropping batch size to 1k");
            batchSize = 1_000;
        } else if (linesGuess < 1_000_000) {
            System.out.println("less than 1,000,000, dropping batch size to 10k");
            batchSize = 10_000;
        }
        br.close();
        // guess delimiter
        char delimiter = ',';
        if (firstLine.contains("|")) {
            delimiter = '|';
        }
        if (firstLine.contains("\t")) {
            delimiter = '\t';
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
        return new LineIteratorAndBatchSize(csvMapper.reader(String[].class).with(schema).readValues(new File(filePath)), batchSize);
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
}