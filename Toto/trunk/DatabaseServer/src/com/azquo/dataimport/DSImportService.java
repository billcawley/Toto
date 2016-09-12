package com.azquo.dataimport;

import com.azquo.memorydb.AzquoMemoryDBConnection;
import com.azquo.memorydb.Constants;
import com.azquo.memorydb.DatabaseAccessToken;
import com.azquo.memorydb.core.AzquoMemoryDB;
import com.azquo.memorydb.core.Name;
import com.azquo.memorydb.service.NameService;
import com.azquo.memorydb.service.ValueService;
import com.azquo.spreadsheet.DSSpreadsheetService;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvParser;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import groovy.lang.GroovyRuntimeException;
import groovy.lang.GroovyShell;
import groovy.lang.Script;

import java.io.*;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
 * <p>
 * Created by cawley on 20/05/15.
 * <p>
 * Has a fair bit of the logic that was in the original import service.
 * Note : large chunks of this were originally written by WFC and then refactored by EFC.
 * <p>
 * Azquo has no schema like an SQL database but to load data a basic set structure needs to be defined
 * and rules for interpreting files need to be also. These two together effectively are the equivalent of an SQL schema.
 * <p>
 * I'm trying to write up as much of the non obvious logic as possible in comments in the code.
 * <p>
 * The cell on a line can be a value or an attribute or a name - or a part of another cell
 */
public class DSImportService {


    // these two are not for clauses, it's to do with reading the file in the first place, do we read the headers or not, how many lines to skip before data
    private static final String HEADINGSSTRING = "HEADINGS";
    private static final String SKIPLINESSTRING = "SKIPLINES";
    // new functionality for pre processing of the file to be handed to a groovy script
    private static final String GROOVYPROCESSOR = "GROOVYPROCESSOR";


    private static final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter ukdf2 = DateTimeFormatter.ofPattern("dd/MM/yy");
    private static final DateTimeFormatter ukdf3 = DateTimeFormatter.ofPattern("dd MMM yyyy");
    private static final DateTimeFormatter ukdf4 = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /*
    Currently only two types of import supported and detection on file name (best idea?). Run the import and persist.
    Sets being as mentioned at the top one of the two files that are needed along with import headers to set up a database ready to load data.

    An entry point to the class functionality.
    */

    public static String readPreparedFile(DatabaseAccessToken databaseAccessToken, String filePath, String fileName, List<String> attributeNames, String user, boolean persistAfter, boolean isSpreadsheet) throws Exception {
        System.out.println("Reading file " + filePath);
        AzquoMemoryDBConnection azquoMemoryDBConnection = DSSpreadsheetService.getConnectionFromAccessToken(databaseAccessToken);
        // in an ad hoc spreadsheet area should it say imported?
        azquoMemoryDBConnection.setProvenance(user, "imported", fileName, "");
        return readPreparedFile(azquoMemoryDBConnection, filePath, fileName, attributeNames, persistAfter, isSpreadsheet);
    }

    // Other entry point into the class functionality,  called by above but also directly from DSSpreadsheet service when it has prepared a CSV from data entered ad-hoc into a sheet

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
            if (sheetLanguage.contains(".")){ // knock off the suffix if it's there. Used to be removed client side, makes more sense here
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
                if (!cell.isEmpty()) { // why we're not just grabbing the first cell fir the set
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
            // Preparatory stuff
            // Local cache of names just to speed things up, the name name could be referenced millions of times in one file
            final Map<String, Name> namesFoundCache = new ConcurrentHashMap<>();
            long track = System.currentTimeMillis();
            char delimiter = ',';
            // try to guess the number of lines
            File sizeTest = new File(filePath);
            final long fileLength = sizeTest.length();
            BufferedReader br = new BufferedReader(new FileReader(filePath));
            // grab the first line to check on delimiters
            String firstLine = br.readLine();
            String secondLine = br.readLine();
            long linesGuess = fileLength / (secondLine != null ? secondLine.length() : 1_000); // a very rough approximation assuming the second line is a typical length.
            System.out.println("Lines guessed at : " + linesGuess);
            int batchSize = 100_000;
            if (linesGuess < 100_000){
                System.out.println("less than 100,000, dropping batch size to 1k");
                batchSize = 1_000;
            } else if (linesGuess < 1_000_000){
                System.out.println("less than 1,000,000, dropping batch size to 10k");
                batchSize = 10_000;
            }
            br.close();
            // guess delimiter
            if (firstLine != null) {
                if (firstLine.contains("|")) {
                    delimiter = '|';
                }
                if (firstLine.contains("\t")) {
                    delimiter = '\t';
                }
            } else {
                return "First line blank"; //if he first line is blank, ignore the sheet
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

            String importInterpreterLookup = fileName; // breaking this off as a new field - used to find names in the db with info to interpret the file
            if (importInterpreterLookup.contains(".")){
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
            // as it says, option to pre process
            if (importInterpreter != null && importInterpreter.getAttribute(GROOVYPROCESSOR) != null) {
                System.out.println("Groovy found! Running  . . . ");
                Object[] groovyParams = new Object[3];
                groovyParams[0] = filePath;
                groovyParams[1] = azquoMemoryDBConnection;
                GroovyShell shell = new GroovyShell();
                try {
                    final Script script = shell.parse(importInterpreter.getAttribute(GROOVYPROCESSOR));
                    filePath = (String) script.invokeMethod("fileProcess", groovyParams);
                } catch (GroovyRuntimeException e) {
                    // exception could be because the groovy script didn't have a function that matched
                    e.printStackTrace();
                    throw new Exception("Groovy error " + e.getMessage());
                }
                System.out.println("Groovy done.");
            }
            // keep this one separate so it can be closed at the end. Accomodating transposing
            MappingIterator<String[]> originalLineIterator = csvMapper.reader(String[].class).with(schema).readValues(new File(filePath));
            Iterator<String[]> lineIterator = originalLineIterator; // for the data, it might be reassigned in the case of transposing
            String[] headers = null;
            int skipLines = 0;
            if (!isSpreadsheet && importInterpreter != null) {
                // hack for spark response, I'll leave in here for the moment, it could be useful for others
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
                // The code below should be none the wiser that a transpose happened if it did.
                String importHeaders = importInterpreter.getAttribute(HEADINGSSTRING);
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
                for (String header:headers){
                    if (header.contains(".") || header.contains(";")){
                        hasClauses = true;
                    }
                }
                if (!hasClauses){
                    if (!lineIterator.hasNext()){
                        throw new Exception("Invalid headers on import file - is this a report that required az_ReportName?");
                    }
                    String[] nextLine = lineIterator.next();
                    int headingCount = 1;
                    boolean lastfilled = true;
                    while (lineIterator.hasNext() && lastfilled && headingCount++ < 10){
                        int colNo = 0;
                        lastfilled = false;
                        for (String heading:nextLine){
                            if (heading.length() > 2 && colNo < headers.length) { //ignore --
                                if (heading.startsWith(".")){
                                    headers[colNo] += heading;
                                }else{
                                    headers[colNo] +=";" + heading;
                                }
                                lastfilled = true;
                            }
                            colNo++;
                        }
                        nextLine = lineIterator.next();
                    }
                }
                if (isSpreadsheet) {
                    Name importSheets = NameService.findOrCreateNameInParent(azquoMemoryDBConnection, "All import sheets", null, false);
                    Name dataImportThis = NameService.findOrCreateNameInParent(azquoMemoryDBConnection, "DataImport " + importInterpreterLookup, importSheets, true);
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
            List<MutableImportHeading> mutableImportHeadings = new ArrayList<>();
            // read the clauses, assign the heading.name if you can find it, add on the context headings
            HeadingReader.readHeaders(azquoMemoryDBConnection, headers, mutableImportHeadings, importInterpreterLookup, attributeNames);
            // further information put into the ImportHeadings based off the initial info
            fillInHeaderInformation(azquoMemoryDBConnection, mutableImportHeadings);
            // convert to immutable. Not strictly necessary, as much for my sanity as anything (EFC) - we do NOT want this info changed by actual data loading
            final List<ImmutableImportHeading> immutableImportHeadings = new ArrayList<>(mutableImportHeadings.size());
            immutableImportHeadings.addAll(mutableImportHeadings.stream().map(ImmutableImportHeading::new).collect(Collectors.toList())); // not sure if stream is that clear here but it gives me a green light from IntelliJ
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

            while (lineIterator.hasNext()) {
                String[] lineValues = lineIterator.next();
      /*  CHANGE OF RULES - WE'LL NEED TO MAKE IT EXPLICIT IF WE ARE TO ACCEPT CARRIAGE RETURNS IN THE MIDDLE OF LINES
          EXPORTS FROM MICROSOFT SQL TRUNCATE NULLS AT THE END OF LINES

            while (lineValues.length < colCount && lineIterator.hasNext()) { // if there are carriage returns in columns, we'll assume on this import that every line must have the same number of columns (may need an option later to miss this)
                String[] additionalValues = lineIterator.next();
                if (additionalValues.length == 0) break;
                if (additionalValues.length >= colCount) {
                    lineValues = additionalValues;
                } else {
                    lineValues[lineValues.length - 1] += "\n" + additionalValues[0];
                    lineValues = (String[]) ArrayUtils.addAll(lineValues, ArrayUtils.subarray(additionalValues, 1, additionalValues.length)); // not sure I like this cast here, will have a think
                }
            }
            */
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
            // wasn't closing before, maybe why the files stayed there
            originalLineIterator.close();
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
            if (t.getCause() != null){ // once should do it, unwrap to reduce java.lang.exception being shown to the user
                t = t.getCause();
            }
            throw new Exception(fileName + " : " + t.getMessage());
        }
    }

    // run through the headers. Mostly this means running through clauses,

    private static LocalDate tryDate(String maybeDate, DateTimeFormatter dateTimeFormatter) {
        try {
            return LocalDate.parse(maybeDate, dateTimeFormatter);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    public static LocalDate isADate(String maybeDate) {
        LocalDate date = tryDate(maybeDate.length() > 10 ? maybeDate.substring(0, 10) : maybeDate, dateTimeFormatter);
        if (date != null) return date;
        date = tryDate(maybeDate.length() > 10 ? maybeDate.substring(0, 10) : maybeDate, ukdf4);
        if (date != null) return date;
        date = tryDate(maybeDate.length() > 11 ? maybeDate.substring(0, 11) : maybeDate, ukdf3);
        if (date != null) return date;
        return tryDate(maybeDate.length() > 8 ? maybeDate.substring(0, 8) : maybeDate, ukdf2);
    }

    /* This is called for all the ; separated clauses in a header e.g. Gender; parent of Customer; child of Genders
    Called multiple times per header. I assume clause is trimmed! */

    /*
    headings are clauses separated by semicolons, first is the heading name then onto the extra stuff
    essentially parsing through all the relevant things in a heading to populate a MutableImportHeading
    */

    /* Used to find component cells for composite values
    The extra logic aside simply from heading matching is the identifier flag (multiple attributes mean many headings with the same name)
    Or attribute being null (thus we don't care about identifier)
    */

    private static ImportCellWithHeading findCellWithHeading(String nameToFind, List<ImportCellWithHeading> importCellWithHeadings) {
        //look for a column with identifier, or, if not found, a column that does not specify an attribute
        ImportCellWithHeading toReturn = null;
        for (ImportCellWithHeading importCellWithHeading : importCellWithHeadings) {
            ImmutableImportHeading heading = importCellWithHeading.getImmutableImportHeading();
            //checking the name itself, then the name as part of a comma separated string
            if (heading.heading != null && (heading.heading.equalsIgnoreCase(nameToFind))
                    && (heading.isAttributeSubject || heading.attribute == null || heading.isDate)) {
                if (heading.isAttributeSubject) {
                    return importCellWithHeading;
                }
                // ah I see the logic here. Identifier means it's the one to use, if not then there must be only one - if more than one are found then it's too ambiguous to work with.
                if (toReturn == null) {
                    toReturn = importCellWithHeading; // our possibility but don't return yet, need to check if there's more than one match
                } else {
                    return null;// found more than one possibility, return null now
                }
            }
        }
        return toReturn;
    }

    /* Very similar to above, used for finding peer and attribute indexes. not sure of an obvious factor - just the middle lines or does this make things more confusing?
    I'd need an interface between Mutable and Immutable import heading, quite a few more lines of code to reduce this slightly.
    Notable that this function now WON'T find a context heading. This is fine.*/

    private static int findMutableHeadingIndex(String nameToFind, List<MutableImportHeading> headings) {
        //look for a column with identifier, or, if not found, a column that does not specify an attribute
        nameToFind = nameToFind.trim();
        int headingFound = -1;
        for (int headingNo = 0; headingNo < headings.size(); headingNo++) {
            MutableImportHeading heading = headings.get(headingNo);
            //checking the name itself, then the name as part of a comma separated string
            if (heading.heading != null && (heading.heading.equalsIgnoreCase(nameToFind) || heading.heading.toLowerCase().startsWith(nameToFind.toLowerCase() + ","))
                    && (heading.isAttributeSubject || heading.attribute == null || heading.isDate)) {
                if (heading.isAttributeSubject) {
                    return headingNo;
                }
                if (headingFound == -1) {
                    headingFound = headingNo;
                } else {
                    return -1;//too many possibilities
                }
            }
        }
        return headingFound;
    }


    // I think the cache is purely a performance thing though it's used for a little logging later (total number of names inserted)

    private static Name findOrCreateNameStructureWithCache(AzquoMemoryDBConnection azquoMemoryDBConnection, Map<String, Name> namesFoundCache, String name, Name parent, boolean local, List<String> attributeNames) throws Exception {
        //namesFound is a quick lookup to avoid going to findOrCreateNameInParent - note it will fail if the name was changed e.g. parents removed by exclusive but that's not a problem
        String np = name + ",";
        if (parent != null) {
            np += parent.getId();
        }
        np += attributeNames.get(0);
        Name found = namesFoundCache.get(np);
        if (found != null) {
            return found;
        }
        found = NameService.findOrCreateNameStructure(azquoMemoryDBConnection, name, parent, local, attributeNames);
        namesFoundCache.put(np, found);
        return found;
    }

    // to make a batch call to the above if there are a list of parents a name should have

    private static Name includeInParents(AzquoMemoryDBConnection azquoMemoryDBConnection, Map<String, Name> namesFoundCache, String name, Set<Name> parents, boolean local, List<String> attributeNames) throws Exception {
        Name child = null;
        if (parents == null || parents.size() == 0) {
            child = findOrCreateNameStructureWithCache(azquoMemoryDBConnection, namesFoundCache, name, null, local, attributeNames);
        } else {
            for (Name parent : parents) {
                child = findOrCreateNameStructureWithCache(azquoMemoryDBConnection, namesFoundCache, name, parent, local, attributeNames);
            }
        }
        return child;
    }

    /* Created by EFC to try to improve speed through multi threading.
     The basic file parsing is single threaded but since this can start while later lines are being read I don't think this is a problem.
     That is to say on a large file the threads will start to stack up fairly quickly
     Adapted to Callable from Runnable - I like the assurances this gives for memory synchronisation
     */

    private static class BatchImporter implements Callable<Void> {

        private final AzquoMemoryDBConnection azquoMemoryDBConnection;
        private final AtomicInteger valueTracker;
        private int lineNo;
        private final List<List<ImportCellWithHeading>> dataToLoad;
        private final Map<String, Name> namesFoundCache;
        private final List<String> attributeNames;

        BatchImporter(AzquoMemoryDBConnection azquoMemoryDBConnection, AtomicInteger valueTracker, List<List<ImportCellWithHeading>> dataToLoad, Map<String, Name> namesFoundCache, List<String> attributeNames, int lineNo) {
            this.azquoMemoryDBConnection = azquoMemoryDBConnection;
            this.valueTracker = valueTracker;
            this.dataToLoad = dataToLoad;
            this.namesFoundCache = namesFoundCache;
            this.attributeNames = attributeNames;
            this.lineNo = lineNo;
        }

        @Override
        public Void call() throws Exception {
            long trigger = 10;
            Long time = System.currentTimeMillis();
            for (List<ImportCellWithHeading> lineToLoad : dataToLoad) {
                /* skip any line that has a blank in the first column unless the first column had no header
                   of course if the first column has no header and then the second has data but not on this line then it would get loaded
                   happy for the check to remain in here - more stuff for the multi threaded bit */
                ImportCellWithHeading first = lineToLoad.get(0);
                if (first.getLineValue().length() > 0 || first.getImmutableImportHeading().heading == null || first.getImmutableImportHeading().compositionPattern!=null) {
                    if (getCompositeValuesCheckOnlyAndExisting(azquoMemoryDBConnection, lineToLoad, lineNo, attributeNames)) {
                        try {
                            // valueTracker simply the number of values imported
                            valueTracker.addAndGet(interpretLine(azquoMemoryDBConnection, lineToLoad, namesFoundCache, attributeNames, lineNo));
                        } catch (Exception e) {
                            azquoMemoryDBConnection.addToUserLogNoException(e.getMessage(), true);
                            throw e;
                        }
                        Long now = System.currentTimeMillis();
                        if (now - time > trigger) {
                            System.out.println("line no " + lineNo + " time = " + (now - time) + "ms");
                        }
                        time = now;
                    }
                }
                lineNo++;
            }
            azquoMemoryDBConnection.addToUserLogNoException("Batch finishing : " + DecimalFormat.getInstance().format(lineNo) + " imported.", true);
            azquoMemoryDBConnection.addToUserLogNoException("Values Imported : " + DecimalFormat.getInstance().format(valueTracker), true);
            return null;
        }
    }

    // sort peer headings, attribute headings, child of, parent of, context peer headings
    // called right after readHeaders, try to do as much checking as possible here. Some of this logic was unnecessarily being done each line

    private static void fillInHeaderInformation(AzquoMemoryDBConnection azquoMemoryDBConnection, List<MutableImportHeading> headings) throws Exception {
        int currentHeadingIndex = 0;
        // use a for loop like this as we need the index
        for (int headingNo = 0; headingNo < headings.size(); headingNo++) {
            MutableImportHeading mutableImportHeading = headings.get(headingNo);
            if (mutableImportHeading.heading != null) {
                // ok find the indexes of peers and get shirty if you can't find them
                // this had a check that is wasn't a context heading so presumably we don't need to do this to context headings
                if (mutableImportHeading.name != null && mutableImportHeading.peers.size() > 0) { // has peers (of course) and a name. Little unsure on the name criteria - could one define peers against no name?
                    for (String peer : mutableImportHeading.peers) {
                        peer = peer.trim();
                        //three possibilities to find the peer:
                        int peerHeadingIndex = findMutableHeadingIndex(peer, headings);
                        if (peerHeadingIndex >= 0) {
                            mutableImportHeading.peerCellIndexes.add(peerHeadingIndex);
                        } else {
                            // when dealing with populating peer headings first look for the headings then look at the context headings, that's what this does - now putting the context names in their own field
                            // note : before all contexts were scanned, this is not correct!
                            int lookForContextIndex = currentHeadingIndex;
                            // the point it to start for the current and look back until we find headings (if we can)
                            while (lookForContextIndex >= 0) {
                                MutableImportHeading check = headings.get(lookForContextIndex);// first time it will be the same as teh heading we're checking
                                if (!check.contextHeadings.isEmpty()) { // then it's this one that's relevant
                                    for (MutableImportHeading contextCheck : check.contextHeadings) {
                                        if (contextCheck.name == null) {// create the name for the context if it doesn't exist
                                            List<String> languages = new ArrayList<>();
                                            languages.add(Constants.DEFAULT_DISPLAY_NAME);
                                            contextCheck.name = NameService.findOrCreateNameInParent(azquoMemoryDBConnection, contextCheck.heading, null, false, languages);
                                        } // then if it matched add it to the peers from context set
                                        if (contextCheck.name != null && contextCheck.name.getDefaultDisplayName().equalsIgnoreCase(peer)) {//WFC: this used to look in the parents of the context name.  Now only looks at the name itself.
                                            mutableImportHeading.peersFromContext.add(contextCheck.name);
                                            peerHeadingIndex = 0;
                                            break;
                                        }
                                    }
                                    break;
                                }
                                lookForContextIndex--;
                            }
                        }
                        if (peerHeadingIndex == -1) {
                            throw new Exception("error: cannot find peer " + peer + " for " + mutableImportHeading.name.getDefaultDisplayName());
                        }
                    }
                }
                // having an attribute means the content of this column relates to a name in another column, need to find that name
                // fairly simple stuff, it's using findMutableHeadingIndex to find the subject of attributes and parents
                fillAttributeAndParentOfForHeading(mutableImportHeading, headings);
                for (MutableImportHeading contextHeading : mutableImportHeading.contextHeadings) {
                    fillAttributeAndParentOfForHeading(contextHeading, headings);
                }
            }
            currentHeadingIndex++;
        }

        /* ok here I'm putting some logic that WAS in the actual line reading relating to context headings and peers etc.
         worth noting that after the above the headings were (I mean in the previous code) made immutable so the state of the headers should be the same
         Since the above loop will populate some context names I'm going to leave this as a separate loop below
         */
        for (MutableImportHeading mutableImportHeading : headings) {
            if (mutableImportHeading.contextHeadings.size() > 0 && mutableImportHeading.name != null) { // ok so some context headings and a name for this column? I guess as in not an attribute column for example
                MutableImportHeading contextPeersHeading = null;
                List<Name> contextNames = new ArrayList<>();
                // gather the context names and peers
                for (MutableImportHeading contextHeading : mutableImportHeading.contextHeadings) {
                    contextNames.add(contextHeading.name);
                    if (!contextHeading.peers.isEmpty()) {
                        contextPeersHeading = contextHeading;
                        if (contextHeading.blankZeroes) {
                            mutableImportHeading.blankZeroes = true;
                        }
                    }
                }
                contextNames.add(mutableImportHeading.name);// add this name onto the context stack - "this" referenced below will mean it's added again but only the first time, on subsequent headings it will be that heading (what with headings inheriting contexts)
                if (contextPeersHeading != null) { // a value cell HAS to have peers, context headings are only for values
                    final Set<Name> namesForValue = new HashSet<>(); // the names we're preparing for values
                    namesForValue.add(contextPeersHeading.name);// ok the "defining" name with the peers.
                    final Set<Integer> possiblePeerIndexes = new HashSet<>(); // the names we're preparing for values
                    boolean foundAll = true;
                    for (String peer : contextPeersHeading.peers) { // ok so a value with peers
                        if (peer.equalsIgnoreCase("this")) {
                            possiblePeerIndexes.add(-1); // can't use 0, this means "this" as in this heading - since context peer indexes are passed along what "this" is will change
                            // essentially an inconsistent use of possiblePeerIndexes - in most cases it refers to the line name and in this case it's the heading name
                        } else {
                            Name possiblePeer = null;
                            for (Name contextPeer : contextNames) {
                                if (contextPeer.getDefaultDisplayName().equalsIgnoreCase(peer)) {
                                    possiblePeer = contextPeer;
                                    namesForValue.add(contextPeer);
                                    break;
                                }
                            }
                            // couldn't find it in the context so look through the headings?
                            if (possiblePeer == null) {
                                int possiblePeerIndex = findMutableHeadingIndex(peer, headings);
                                if (possiblePeerIndex == -1) {
                                    foundAll = false;
                                    break;
                                } else {
                                    possiblePeerIndexes.add(possiblePeerIndex);
                                }
                            }
                        }
                    }
                    if (foundAll) { // the peers based of indexes will not of course have been checked but we have a set of names which have been checked and indexes to check against
                        mutableImportHeading.contextPeersFromContext = namesForValue;
                        mutableImportHeading.contextPeerCellIndexes = possiblePeerIndexes;
                    }
                }
            }
        }
        // and finally, detect whether each column will be referencing names or not
        Set<Integer> indexesNeedingNames = new HashSet<>();
        for (MutableImportHeading mutableImportHeading : headings) {
            indexesNeedingNames.addAll(mutableImportHeading.peerCellIndexes);
            indexesNeedingNames.addAll(mutableImportHeading.contextPeerCellIndexes);
            if (mutableImportHeading.indexForChild != -1) {
                indexesNeedingNames.add(mutableImportHeading.indexForChild);
            }
            if (mutableImportHeading.indexForAttribute != -1) {
                indexesNeedingNames.add(mutableImportHeading.indexForChild);
            }
        }
        for (int i = 0; i < headings.size(); i++) {
            MutableImportHeading mutableImportHeading = headings.get(i);
            mutableImportHeading.lineNameRequired = mutableImportHeading.indexForChild != -1 || !mutableImportHeading.parentNames.isEmpty() || indexesNeedingNames.contains(i) || mutableImportHeading.isAttributeSubject;
        }
    }

    // peers in the headings might have caused some database modification but really it is here that things start to be modified in earnest
    private static int interpretLine(AzquoMemoryDBConnection azquoMemoryDBConnection, List<ImportCellWithHeading> cells, Map<String, Name> namesFoundCache, List<String> attributeNames, int lineNo) throws Exception {
        int valueCount = 0;
        // initial pass to deal with defaults, dates and local parents
        //set defaults before dealing with local parent/child
        for (ImportCellWithHeading importCellWithHeading : cells) {
            if (importCellWithHeading.getImmutableImportHeading().defaultValue != null && importCellWithHeading.getLineValue().length() == 0) {
                importCellWithHeading.setLineValue(importCellWithHeading.getImmutableImportHeading().defaultValue);
            }
        }
        for (ImportCellWithHeading importCellWithHeading : cells) {
            // this basic value checking was outside, I see no reason it shouldn't be in here
            if (importCellWithHeading.getImmutableImportHeading().attribute != null && importCellWithHeading.getImmutableImportHeading().isDate) {
                    /*
                    interpret the date and change to standard form
                    todo consider other date formats on import - these may  be covered in setting up dates, but I'm not sure - WFC
                    */
                LocalDate date = isADate(importCellWithHeading.getLineValue());
                if (date != null) {
                    importCellWithHeading.setLineValue(dateTimeFormatter.format(date));
                }
            }
            /* 3 headings. Town, street, whether it's pedestrianized. Pedestrianized parent of street. Town parent of street local.
             the key here is that the resolveLineNameParentsAndChildForCell has to resolve line Name for both of them - if it's called on "Pedestrianized parent of street" first
             both pedestrianized (ok) and street (NOT ok!) will have their line names resolved
             whereas resolving "Town parent of street local" first means that the street should be correct by the time we resolve "Pedestrianized parent of street".
             essentially sort all local names */
            if (importCellWithHeading.getImmutableImportHeading().lineNameRequired && importCellWithHeading.getImmutableImportHeading().isLocal) {
                // local and it is a parent of another heading (has child heading), inside this function it will use the child heading set up
                resolveLineNameParentsAndChildForCell(azquoMemoryDBConnection, namesFoundCache, importCellWithHeading, cells, attributeNames, lineNo);
            }
        }
        // now sort non local names
        for (ImportCellWithHeading cell : cells) {
            if (cell.getImmutableImportHeading().lineNameRequired && !cell.getImmutableImportHeading().isLocal) {
                resolveLineNameParentsAndChildForCell(azquoMemoryDBConnection, namesFoundCache, cell, cells, attributeNames, lineNo);
            }
        }

        long tooLong = 2; // now ms
        long time = System.currentTimeMillis();
        // now do the peers
        for (ImportCellWithHeading cell : cells) {
            /* ok the gist seems to be that there's peers as defined in a context item in which case it's looking in context items and peers
            peers as defined in the context will look for other columns and in the context (those in the context having been pre prepared)
             */
            boolean peersOk = true;
            final Set<Name> namesForValue = new HashSet<>(); // the names we're going to look for for this value
            if (!cell.getImmutableImportHeading().contextPeersFromContext.isEmpty() || !cell.getImmutableImportHeading().contextPeerCellIndexes.isEmpty()) { // new criteria,this means there are context peers to deal with
                namesForValue.addAll(cell.getImmutableImportHeading().contextPeersFromContext);// start with the ones we have to hand, including the main name
                for (int peerCellIndex : cell.getImmutableImportHeading().contextPeerCellIndexes) {
                    // Clarified now - normally contextPeerCellIndexes refers to the line name but if it's "this" then it's the heading name. Inconsistent.
                    if (peerCellIndex == -1) {
                        namesForValue.add(cell.getImmutableImportHeading().name);
                    } else {
                        ImportCellWithHeading peerCell = cells.get(peerCellIndex);
                        if (peerCell.getLineName() != null) {
                            namesForValue.add(peerCell.getLineName());
                        } else {// fail - I plan to have resolved all possible line names by this point!
                            peersOk = false;
                            break;
                        }
                    }
                }
                // can't have more than one peers defined so if not from context check standard peers - peers from context is as it says, from context but not defined in there!
            } else if (!cell.getImmutableImportHeading().peerCellIndexes.isEmpty() || !cell.getImmutableImportHeading().peersFromContext.isEmpty()) {
                namesForValue.add(cell.getImmutableImportHeading().name); // the one at the top of this headingNo, the name with peers.
                // the old logic added the context peers straight in so I see no problem doing this here - this is what might be called inherited peers, from a col to the left.
                // On the col where context peers are defined normal peers should not be defined or used
                namesForValue.addAll(cell.getImmutableImportHeading().peersFromContext);
                // Ok I had to stick to indexes to get the cells
                for (Integer peerCellIndex : cell.getImmutableImportHeading().peerCellIndexes) { // go looking for non context peers
                    ImportCellWithHeading peerCell = cells.get(peerCellIndex); // get the cell
                    if (peerCell.getLineName() != null) {// under new logic the line name would have been created if possible so if it's not there fail
                        namesForValue.add(peerCell.getLineName());
                    } else {
                        peersOk = false;
                        break; // no point continuing gathering the names
                    }
                }
            }
            if (peersOk && !namesForValue.isEmpty()) { // no point storing if peers not ok or no names for value (the latter shouldn't happen, braces and a belt I suppose)
                // now we have the set of names for that name with peers get the value from that headingNo it's a header for
                String value = cell.getLineValue();
                if (!(cell.getImmutableImportHeading().blankZeroes && isZero(value)) && value.trim().length() > 0) { // don't store if blank or zero and blank zeroes
                    valueCount++;
                    // finally store our value and names for it
                    ValueService.storeValueWithProvenanceAndNames(azquoMemoryDBConnection, value, namesForValue);
                }
            }
            // ok that's the peer/value stuff done I think, now onto attributes
            if (cell.getImmutableImportHeading().indexForAttribute >= 0 && cell.getImmutableImportHeading().attribute != null
                    && cell.getLineValue().length() > 0) {
                // handle attribute was here, we no longer require creating the line name so it can in lined be cut down a lot
                ImportCellWithHeading identityCell = cells.get(cell.getImmutableImportHeading().indexForAttribute); // get our source cell
                if (identityCell.getLineName() != null) {
                    identityCell.getLineName().setAttributeWillBePersisted(cell.getImmutableImportHeading().attribute, cell.getLineValue().replace("\\\\t","\t").replace("\\\\n","\n"));
                }
                // else an error? If the line name couldn't be made in resolveLineNamesParentsChildren above there's nothing to be done about it
            }
            long now = System.currentTimeMillis();
            if (now - time > tooLong) {
                System.out.println(cell.getImmutableImportHeading().heading + " took " + (now - time) + "ms");
            }
            time = System.currentTimeMillis();
        }
        return valueCount;
    }

    // factored to make applying this to context headings easier

    private static void fillAttributeAndParentOfForHeading(MutableImportHeading mutableImportHeading, List<MutableImportHeading> headings) throws Exception {
        if (mutableImportHeading.attribute != null) { // && !importHeading.attribute.equals(Constants.DEFAULT_DISPLAY_NAME)) {
            String headingName = mutableImportHeading.heading;
            // so if it's Customer,Address1 we need to find customer.
            // This findHeadingIndex will look for the Customer with isAttributeSubject = true or the first one without an attribute
            // attribute won't be context
            mutableImportHeading.indexForAttribute = findMutableHeadingIndex(headingName, headings);
            if (mutableImportHeading.indexForAttribute >= 0) {
                headings.get(mutableImportHeading.indexForAttribute).isAttributeSubject = true;//it may not be true (as in found due to no attribute rather than language), in which case set it true now . . .need to consider this logic
                // so now we have an attribute subject for this name go through all columns for other headings with the same name
                // and if the attribute is NOT set then default it and set indexForAttribute to be this one. How much would this be practically used??
                // some unclear logic here, this needs refactoring
                for (MutableImportHeading heading2 : headings) {
                    //this is for the cases where the default display name is not the identifier.
                    if (heading2.heading != null && heading2.heading.equals(mutableImportHeading.heading) && heading2.attribute == null) {
                        heading2.attribute = Constants.DEFAULT_DISPLAY_NAME;
                        heading2.indexForAttribute = mutableImportHeading.indexForAttribute;
                        break;
                    }
                }
            }
        }
        // parent of being in context of this upload, if you can't find the heading throw an exception
        if (mutableImportHeading.parentOfClause != null) {
            mutableImportHeading.indexForChild = findMutableHeadingIndex(mutableImportHeading.parentOfClause, headings);
            if (mutableImportHeading.indexForChild < 0) {
                throw new Exception("error: cannot find column " + mutableImportHeading.parentOfClause + " for child of " + mutableImportHeading.heading);
            }
        }
    }

    private static List<String> setLocalLanguage(ImmutableImportHeading heading, List<String> defaultLanguages) {
        List<String> languages = new ArrayList<>();
        if (heading.attribute != null) {
            languages.add(heading.attribute);
        } else {
            languages.addAll(defaultLanguages);
        }
        return languages;
    }

    // namesFound is a cache. Then the heading we care about then the list of all headings.
    // This used to be called handle parent and deal only with parents and children but it also resolved line names. Should be called for local first then non local
    // it tests to see if the current line name is null or not as it may have been set by a call to resolveLineNamesParentsChildrenRemove on a different cell setting the child name
    private static void resolveLineNameParentsAndChildForCell(AzquoMemoryDBConnection azquoMemoryDBConnection, Map<String, Name> namesFoundCache,
                                                       ImportCellWithHeading cellWithHeading, List<ImportCellWithHeading> cells, List<String> attributeNames, int lineNo) throws Exception {
        if (cellWithHeading.getLineValue().length() == 0) { // so nothing to do
            return;
        }
        /* ok this is important - I was adjusting cellWithHeading.getLineValue() to add quotes,
        this was not clever as it's referenced in other places (goes back to the idea that ideally this would be immutable)
        So make a local reference to add quotes to or whatever
         */
        String cellWithHeadingLineValue = cellWithHeading.getLineValue();
        if (cellWithHeadingLineValue.contains(",") && !cellWithHeadingLineValue.contains(Name.QUOTE + "")) {//beware of treating commas in cells as set delimiters....
            cellWithHeadingLineValue = Name.QUOTE + cellWithHeadingLineValue + Name.QUOTE;
        }
        if (cellWithHeading.getLineName() == null) { // then create it, this will take care of the parents ("child of") while creating
            cellWithHeading.setLineName(includeInParents(azquoMemoryDBConnection, namesFoundCache, cellWithHeadingLineValue
                    , cellWithHeading.getImmutableImportHeading().parentNames, cellWithHeading.getImmutableImportHeading().isLocal, setLocalLanguage(cellWithHeading.getImmutableImportHeading(), attributeNames)));
        } else { // it existed (created below as a child name), sort parents if necessary
            for (Name parent : cellWithHeading.getImmutableImportHeading().parentNames) { // apparently there can be multiple child ofs, put the name for the line in the appropriate sets, pretty vanilla based off the parents set up
                parent.addChildWillBePersisted(cellWithHeading.getLineName());
            }
        }
        // ok that's "child of" (as in for names) done
        // now for "parent of", the, child of this line
        if (cellWithHeading.getImmutableImportHeading().indexForChild != -1) {
            ImportCellWithHeading childCell = cells.get(cellWithHeading.getImmutableImportHeading().indexForChild);
            if (childCell.getLineValue().length() == 0) {
                throw new Exception("Line " + lineNo + ": blank value for child of " + cellWithHeading.getLineValue() + " " + cellWithHeading.getImmutableImportHeading().heading);
            }

            // ok got the child cell, need to find the child cell name to add it to this cell's children
            // I think here's it's trying to add to the cells name
            if (childCell.getLineName() == null) {
                childCell.setLineName(findOrCreateNameStructureWithCache(azquoMemoryDBConnection, namesFoundCache, childCell.getLineValue(), cellWithHeading.getLineName()
                        , cellWithHeading.getImmutableImportHeading().isLocal, setLocalLanguage(childCell.getImmutableImportHeading(), attributeNames)));
            } else { // check exclusive logic, only if the child cell line name exists then remove the child from parents if necessary - this replaces the old "remove from" funcitonality
                // the exclusiveSetToCheckAgainst means that if the child we're about to sort has a parent in this set we need to get rid of it before re adding the child to the new location
                Collection<Name> exclusiveSetToCheckAgainst = null;
                if ("".equals(cellWithHeading.getImmutableImportHeading().exclusive) && cellWithHeading.getImmutableImportHeading().parentNames.size() == 1) {
                    // blank exclusive clause, use child of if there's one (check all the way down. all children, necessary due due to composite option name1->name2->name3->etc
                    exclusiveSetToCheckAgainst = cellWithHeading.getImmutableImportHeading().parentNames.iterator().next().findAllChildren();
                } else if (cellWithHeading.getImmutableImportHeading().exclusive != null) { // exclusive is referring to a higher name
                    Name specifiedExclusiveSet = NameService.findByName(azquoMemoryDBConnection, cellWithHeading.getImmutableImportHeading().exclusive);
                    if (specifiedExclusiveSet != null) {
                        specifiedExclusiveSet.removeFromChildrenWillBePersisted(childCell.getLineName()); // if it's directly against the top it won't be caught by the set below, don't want to add to the set I'd have to make a new, potentially large, set
                        exclusiveSetToCheckAgainst = specifiedExclusiveSet.findAllChildren();
                    }
                }
                if (exclusiveSetToCheckAgainst != null) {
                    // essentially if we're saying that this heading is a category e.g. swimwear and we're about to add another name (a swimsuit one assumes) then go through other categories removing the swimsuit from them if it is in there
                    for (Name nameToRemoveFrom : childCell.getLineName().getParents()) {
                        if (exclusiveSetToCheckAgainst.contains(nameToRemoveFrom) && nameToRemoveFrom != cellWithHeading.getLineName()) { // the existing parent is one to be zapped by exclusive criteria and it's not the one we're about to add
                            nameToRemoveFrom.removeFromChildrenWillBePersisted(childCell.getLineName());
                        }
                    }
                }
            }
            cellWithHeading.getLineName().addChildWillBePersisted(childCell.getLineName());
        }
    }

    // replace things in quotes with values from the other columns. So `A column name`-`another column name` might be created as 123-235 if they were the values
    // now seems to support basic excel like string operations, left right and mid. Checking only and existing means "should we import the line at all" bases on these criteria

    private static boolean getCompositeValuesCheckOnlyAndExisting(AzquoMemoryDBConnection azquoMemoryDBConnection, List<ImportCellWithHeading> cells, int lineNo, List<String> languages) {
        int adjusted = 2;
        //loops in case there are multiple levels of dependencies
        while (adjusted > 1) {
            adjusted = 0;
            for (ImportCellWithHeading cell : cells) {
                if (cell.getImmutableImportHeading().compositionPattern != null) {
                    String result = cell.getImmutableImportHeading().compositionPattern;
                    // do line number first, I see no reason not to
                    String LINENO = "LINENO";
                    result = result.replace(LINENO, lineNo + "");
                    int headingMarker = result.indexOf("`");
                    while (headingMarker >= 0) {
                        int headingEnd = result.indexOf("`", headingMarker + 1);
                        if (headingEnd > 0) {
                            String expression = result.substring(headingMarker + 1, headingEnd);
                            String function = null;
                            int funcInt = 0;
                            int funcInt2 = 0;
                            if (expression.contains("(")) {
                                int bracketpos = expression.indexOf("(");
                                function = expression.substring(0, bracketpos);
                                int commaPos = expression.indexOf(",", bracketpos + 1);
                                int secondComma;
                                if (commaPos > 0) {
                                    secondComma = expression.indexOf(",", commaPos + 1);
                                    String countString;
                                    try {
                                        if (secondComma < 0) {
                                            countString = expression.substring(commaPos + 1, expression.length() - 1);
                                            funcInt = Integer.parseInt(countString.trim());
                                        } else {
                                            countString = expression.substring(commaPos + 1, secondComma);
                                            funcInt = Integer.parseInt(countString.trim());
                                            countString = expression.substring(secondComma + 1, expression.length() - 1);
                                            funcInt2 = Integer.parseInt(countString);
                                        }
                                    } catch (Exception ignore) {
                                    }
                                    expression = expression.substring(bracketpos + 1, commaPos);
                                }
                            }
                            ImportCellWithHeading compCell = findCellWithHeading(expression, cells);
                            if (compCell != null) {
                                String sourceVal = compCell.getLineValue();
                                // the two ints need to be as they are used in excel
                                if (function != null && (funcInt > 0 || funcInt2 > 0) && sourceVal.length() > funcInt) {
                                    if (function.equalsIgnoreCase("left")) {
                                        sourceVal = sourceVal.substring(0, funcInt);
                                    }
                                    if (function.equalsIgnoreCase("right")) {
                                        sourceVal = sourceVal.substring(sourceVal.length() - funcInt);
                                    }
                                    if (function.equalsIgnoreCase("mid")) {
                                        //the second parameter of mid is the number of characters, not the end character
                                        sourceVal = sourceVal.substring(funcInt - 1, (funcInt - 1) + funcInt2);
                                    }
                                }
                                result = result.replace(result.substring(headingMarker, headingEnd + 1), sourceVal);
                            }
                        }
                        headingMarker = result.indexOf("`", headingMarker + 1);
                    }
                    if (result.toLowerCase().startsWith("calc")) {
                        result = result.substring(5);
                        Pattern p = Pattern.compile("[\\+\\-\\*\\/]");
                        Matcher m = p.matcher(result);
                        if (m.find()) {
                            double dresult = 0.0;
                            try {
                                double first = Double.parseDouble(result.substring(0, m.start()));
                                double second = Double.parseDouble(result.substring(m.end()));
                                char c = m.group().charAt(0);
                                switch (c) {
                                    case '+':
                                        dresult = first + second;
                                        break;
                                    case '-':
                                        dresult = first - second;
                                        break;
                                    case '*':
                                        dresult = first * second;
                                        break;
                                    case '/':
                                        dresult = first / second;
                                        break;

                                }
                            } catch (Exception ignored) {
                            }
                            result = dresult + "";
                        }
                    }
                    if (!result.equals(cell.getLineValue())) {
                        cell.setLineValue(result);
                        adjusted++;
                    }
                }
                if (cell.getImmutableImportHeading().only != null) {
                    //`only' can have wildcards  '*xxx*'
                    String only = cell.getImmutableImportHeading().only.toLowerCase();
                    String lineValue = cell.getLineValue().toLowerCase();
                    if (only.startsWith("*")) {
                        if (only.endsWith("*")) {
                            if (!lineValue.contains(only.substring(1, only.length() - 1))) {
                                return false;
                            }
                        } else if (!lineValue.startsWith(only.substring(1))) {
                            return false;
                        }
                    } else if (only.endsWith("*")) {
                        if (!lineValue.startsWith(only.substring(0, only.length() - 1))) {
                            return false;
                        }
                    } else {
                        if (!lineValue.equals(only)) {
                            return false;
                        }
                    }
                }
                // we could be deriving the name from composite so check existing here
                if (cell.getImmutableImportHeading().existing) {
                    if (cell.getImmutableImportHeading().attribute != null && cell.getImmutableImportHeading().attribute.length() > 0) {
                        languages = Collections.singletonList(cell.getImmutableImportHeading().attribute);
                    }
                    if (languages == null) { // same logic as used when creating the line names, not sure of this
                        languages = Collections.singletonList(Constants.DEFAULT_DISPLAY_NAME);
                    }
                    // note I'm not going to check parentNames are not empty here, if someone put existing wihthout specifying child of then I think it's fair to say the line isn't valid
                    for (Name parent : cell.getImmutableImportHeading().parentNames) { // try to find any names from anywhere
                        if (!azquoMemoryDBConnection.getAzquoMemoryDB().getNamesForAttributeNamesAndParent(languages, cell.getLineValue(), parent).isEmpty()) { // NOT empty, we found one!
                            return true; // no point carrying on
                        }
                    }
                    return false; // none found
                }
            }
        }
        return true;
    }

    private static boolean isZero(String text) {
        try {
            double d = Double.parseDouble(text);
            return d == 0.0;
        } catch (Exception e) {
            return true;
        }
    }
}