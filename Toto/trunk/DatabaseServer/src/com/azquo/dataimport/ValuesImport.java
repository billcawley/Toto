package com.azquo.dataimport;

import com.azquo.ThreadPools;
import com.azquo.memorydb.AzquoMemoryDBConnection;
import com.azquo.memorydb.Constants;
import com.azquo.memorydb.core.Name;
import com.azquo.memorydb.service.NameService;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvParser;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import groovy.lang.GroovyRuntimeException;
import groovy.lang.GroovyShell;
import groovy.lang.Script;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/*
Edd Aug 2018
THe importing is becoming quite complex and opaque. I'm hoping that breaking things up will help to bring it under control.

THis class should do basic preparation e.g. guessing the number of lines and then batching up data to be loaded.
The actual loading and complex header resolution should be done in other classes, Im trying to break it up.

The cell on a line can be a value or an attribute or a name - or a part of another cell via composite. Or, now, an attribute name.

 */
public class ValuesImport {

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

    /* Calls header validation and batches up the data with headers ready for batch importing. Get headings first,
    they can be in a name or in the file, if in a file then they will be set on a name after for future reference.
    The key is to set up info in names so a file can be uploaded from a client "as is".

    This function itself is relatively simple but the functions it calls getHeadersWithIteratorAndBatchSize and BatchImporter contain some complex logic.
    */

    static String valuesImport(final AzquoMemoryDBConnection azquoMemoryDBConnection, String filePath, String fileName, String zipName, boolean isSpreadsheet, AtomicInteger valuesModifiedCounter) throws Exception {
        try {
            long track = System.currentTimeMillis();
            /* A fair amount is going on in here checking various upload options and parsing the headers. It delivers the
            parsed headings, the iterator for the data lines and how many lines should be processed by each task in the thread pool

            New Ed Broking logic wants to do a combination of lookup initially based on the first half of the zip name then using the first half of the file name
            in language as a way of "versioning" the headers. The second half of the zip name is held to the side to perhaps be replaced in headers later too.
            */
            List<String> languages = Constants.DEFAULT_DISPLAY_NAME_AS_LIST;
            if (zipName != null) {
                languages = new ArrayList<>();
                languages.add(fileName.substring(0, fileName.indexOf(" ")));
                languages.add(Constants.DEFAULT_DISPLAY_NAME);
            }
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
            int linesImported = 0;
            int lineNo = 1; // for error checking etc. Generally the first line of data is line 2, this is incremented at the beginning of the loop. Might be inaccurate if there are vertically stacked headers.
            while (headingsWithIteratorAndBatchSize.lineIterator.hasNext()) { // the main line reading loop
                String[] lineValues = headingsWithIteratorAndBatchSize.lineIterator.next();
                lineNo++;
                linesImported++;
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
                    if (lineValue.startsWith("'") && lineValue.indexOf("'",1)<0){
                        lineValue = lineValue.substring(1);//in Excel insertion of an initial ' means it is a string.
                    }
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
                } else {
                    linesImported--; // drop it down as we're not even going to try that line
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
                    .append(" second(s) to import ")
                    .append(linesImported - linesRejected.size())
                    .append(" lines").append(", ").append(valuesModifiedCounter).append(" values adjusted");

            // add a bit of feedback for rejected lines. Factor? It's not complex stuff.
            // todo - lines blank or corrupt readout
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

    // A simple lookup of headers in the database, headers being a single string held as an attribute in this case

    static Name findInterpreter(AzquoMemoryDBConnection azquoMemoryDBConnection, String importInterpreterLookup, List<String> languages) throws Exception {
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

    // The function to make the an instance of HeadingsWithIteratorAndBatchSize. It will itself call a few utility functions as well as the HeadingReader class.

    private static HeadingsWithIteratorAndBatchSize getHeadersWithIteratorAndBatchSize(AzquoMemoryDBConnection azquoMemoryDBConnection
            , String importInterpreterLookup, String zipName, boolean isSpreadsheet, String filePath, List<String> languages) throws Exception {
        String importAttribute = null;
        Name importInterpreter = null;
        Name assumptions = null;
        String zipVersion = null;
        // prepares for the more complex "headings as children with attributes" method of importing
        // a bunch of files in a zip file,
        if (zipName != null && zipName.length() > 0 && zipName.indexOf(" ") > 0){// EFC - so if say it was "Risk Apr-18.zip" we have Apr-18 as the zipVersion.
            // this is passed through to preProcessHeadersAndCreatePivotSetsIfRequired
            // it isused as a straight replacement e.g. that Apr-18 in something like
            // composition `Policy No` `Policy Issuance Date`;parent of Policy Line;child of ZIPVERSION;required
            // EFC note - this seems a bit hacky, specific to Ed Broking
            zipVersion = zipName.substring(zipName.indexOf(" ")).trim();

            String zipPrefix = zipName.substring(0,zipName.indexOf(" ")); // e.g. Risk
            importInterpreter = NameService.findByName(azquoMemoryDBConnection,"dataimport " + zipPrefix);
            // so the attribute might be "HEADINGS RISK" assuming the file was "Risk Apr-18.zip"
            importAttribute = "HEADINGS " + zipPrefix;
            String importFile;
            if (filePath.contains("/")) {
                importFile = filePath.substring(filePath.lastIndexOf("/")+1);
            } else {
                importFile = filePath.substring(filePath.lastIndexOf("\\")+1);
            }
            int blankPos = importFile.indexOf(" ");
            if (blankPos > 0){
                /* EFC - this looks hacky. more string literals, So it's something like "zip file name assumptions firstbitofimportfilename"
                it turns out assumptions is a sheet in a workbook - it gets put into All Import Sheets as usual
                BUT there's also this name e.g. "Risk test2 Assumptions RLD" which is in Risk test2 Assumptions which is in Import Assumptions
                notably this isn't set as part of any code, it's created when the assumptions file is uploaded, that it should match is based on the headings in tha file
                matching. Rather fragile I'd say.
                Assumptions in the example simply has the attribute "COVERHOLDER NAME" which has the value "Unknown Coverholder"

                Note : assumptions unsued at the moment - todo - clarify the situation and maybe remove?
                */
                assumptions = NameService.findByName(azquoMemoryDBConnection,zipName + " assumptions " + importFile.substring(0,blankPos));
            }
        }
        // so we got the import attribute based off the beginning of the zip name, same for he import interpreter, the former starts HEADINGS, the latter dataimport
        // assumptions is a name found if created by an import file in the normal way. zip version is the end of the zip file name, at the moment a date e.g. Feb-18
        // now standard import interpreter check - EFC note - should this look at zip name too?
        if (importInterpreter == null){
            importInterpreter = findInterpreter(azquoMemoryDBConnection, importInterpreterLookup, languages);
        }
        // check if that name (assuming it's not null!) has groovy in an attribute
        filePath = checkGroovy(azquoMemoryDBConnection, filePath, importInterpreter);
        if (importInterpreter != null && !maybeHasHeadings(filePath)) {
            isSpreadsheet = false;//allow data uploaded in a spreadsheet to use pre-configured headings. isSpreadsheet could also be seen as data with the headings attached
        }
        // checks the first few lines to sort batch size and get a hopefully correctly configured line iterator. Nothing to do with heading interpretation
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

        // held for Ed broking stuff, might be factored in a mo
        Map<String,String> topHeadings = new HashMap<>();

        headers = EdBrokingExtension.checkForImportNameChildren(azquoMemoryDBConnection,headers,importInterpreter,importAttribute,languages,lineIteratorAndBatchSize,topHeadings);
        if (headers == null){
            return null;
        }

        // We might use the headers on the data file, this is notably used when setting up the headers themselves.
        if (headers.size() == 0) {
            headers = EdBrokingExtension.getNextLine(lineIteratorAndBatchSize);
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
        // so we record the original size then jam all the top headings on to the end . . .
        // is there a reason this and the adding of default isn't done above? Does it need the checkRequiredHeadings and preProcessHeadersAndCreatePivotSetsIfRequired
        int topHeadingPos = headers.size();
        if (topHeadings!=null){
            headers.addAll(topHeadings.keySet());
        }
        EdBrokingExtension.checkRequiredHeadings(azquoMemoryDBConnection,headers,importInterpreter,  assumptions, languages);
        // internally can further adjust the headings based off a name attributes. See HeadingReader for details.
        headers = HeadingReader.preProcessHeadersAndCreatePivotSetsIfRequired(azquoMemoryDBConnection, headers, importInterpreter, zipVersion, importInterpreterLookup, languages);//attribute names may have additions when language is in the context
        if (topHeadings !=null){
            for (String topHeading:topHeadings.keySet()){
                headers.set(topHeadingPos, headers.get(topHeadingPos++) + ";default " + topHeadings.get(topHeading));
            }
        }
        if (assumptions!=null){
            for (int i=0;i<headers.size();i++){
                String header = headers.get(i);
                String[] clauses = header.split(";");
                String assumption = assumptions.getAttribute(clauses[0]);
                if (assumption!=null) {
                    headers.set(i, header + ";default " + assumption);
                }
            }
        }
        lineIteratorAndBatchSize.headings = HeadingReader.readHeaders(azquoMemoryDBConnection, headers, languages);
        return lineIteratorAndBatchSize;
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

    // todo - can we just get rid of this? I don't think anyone is using it
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
}