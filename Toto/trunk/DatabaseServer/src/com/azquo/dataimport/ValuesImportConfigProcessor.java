package com.azquo.dataimport;

import com.azquo.memorydb.core.AzquoMemoryDB;
import com.azquo.memorydb.core.Name;
import com.azquo.memorydb.service.NameService;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvParser;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import groovy.lang.GroovyRuntimeException;
import groovy.lang.GroovyShell;
import groovy.lang.Script;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import static com.azquo.dataimport.ValuesImport.ALLIMPORTSHEETS;

/*

Will process a ValuesImportConfig until it's reasy to be used by Values import.

Complex stuff such as finding headers which can be in a name or names in the db or attached to the uploaded file
and then resolving these headers is done or called from here.

Headings are often found in the DB, put there by setup files, to enable importing of client files "as is".

If this class ends as I like it will be a sequence of modifications to valuesImportConfig so that modifying or disabling logic should be easy

 */


class ValuesImportConfigProcessor {

    // Attribute names for a name in the database with importing instructions.
    // is there a groovy pre processor?
    private static final String PREPROCESSOR = "pp";
    // override default encoding
    private static final String FILEENCODING = "FILEENCODING";
    // to do with reading the file in the first place, are the headings already in the DB as an attribute?
    private static final String HEADINGSSTRING = "HEADINGS";
    // how many lines to skip before data?
    private static final String SKIPLINESSTRING = "SKIPLINES";

    static void prepareValuesImportConfig(ValuesImportConfig valuesImportConfig) throws Exception {
        EdBrokingExtension.checkImportFormatterLanguage(valuesImportConfig);
        // now step through what getHeadersWithIteratorAndBatchSize was doing
        EdBrokingExtension.checkImportFormat(valuesImportConfig);
        // now standard import interpreter check - we just checked according to teh zip file
        checkImportInterpreter(valuesImportConfig);
        checkGroovy(valuesImportConfig);
        // if it was flagged as from a spreadsheet the system will assume it has headings, if it doens't switch that flag back
        checkMaybeHasHeadings(valuesImportConfig);
        // checks the first few lines to sort batch size and get a hopefully correctly configured line iterator. Nothing to do with heading interpretation
        setLineIteratorAndBatchSize(valuesImportConfig);
        // transpose the file and reassign the line iterator if required, this was for spark response, we'll leave it in for the moment
        // The code below should be none the wiser that a transpose happened if it did.
        checkTranspose(valuesImportConfig);
        checkSkipLinesAndSimpleImportInterpreterHeaders(valuesImportConfig);
        EdBrokingExtension.checkForImportNameChildren(valuesImportConfig);
        checkHeadingsOnTheFileAndSkipLines(valuesImportConfig);
        EdBrokingExtension.checkRequiredHeadings(valuesImportConfig);
        //attribute names may have additions when language is in the context);
        // I'm not currently making the heading reader deal with a ValuesImportConfig object
        HeadingReader.preProcessHeadersAndCreatePivotSetsIfRequired(valuesImportConfig);
        EdBrokingExtension.dealWithAssumptions(valuesImportConfig);
        // finally resolve them with the HeadingReader and we're good to go
        HeadingReader.readHeaders(valuesImportConfig);
    }


    // the standard way of finding some headings in teh database. Checks they're not set in case the new Ed Broking way set the interpreter
    private static void checkImportInterpreter(ValuesImportConfig valuesImportConfig) throws Exception {
         String importInterpreterLookup = valuesImportConfig.getFileSource();
        if (importInterpreterLookup==null|| importInterpreterLookup.length()==0){
            importInterpreterLookup =  valuesImportConfig.getFileName();
        }
        if (valuesImportConfig.getImportInterpreter() == null) {
            if (importInterpreterLookup.contains(".")) {
                importInterpreterLookup = importInterpreterLookup.substring(0, importInterpreterLookup.lastIndexOf("."));
            }
            // try to find a name which might have the headings in its attributes
            Name importInterpreter = NameService.findByName(valuesImportConfig.getAzquoMemoryDBConnection(), "dataimport " + importInterpreterLookup, valuesImportConfig.getLanguages());
            //we can use the import interpreter to import different files by suffixing the name with _ or a space and suffix.
            while (importInterpreter == null && (importInterpreterLookup.contains(" ") || importInterpreterLookup.contains("_"))) {
                //There may, though, be separate interpreters for A_B_xxx and A_xxx, so we try A_B first
                if (importInterpreterLookup.contains(" ")) {
                    importInterpreterLookup = importInterpreterLookup.substring(0, importInterpreterLookup.lastIndexOf(" "));
                } else {
                    importInterpreterLookup = importInterpreterLookup.substring(0, importInterpreterLookup.lastIndexOf("_"));
                }
                importInterpreter = NameService.findByName(valuesImportConfig.getAzquoMemoryDBConnection(), "dataimport " + importInterpreterLookup, valuesImportConfig.getLanguages());
            }
            valuesImportConfig.setImportInterpreter(importInterpreter);
        }
    }

    // typically groovy scripts write out to a different file, helps a lot for debugging! Most of the time with no groovy specified will just not modify anything
    private static void checkGroovy(ValuesImportConfig valuesImportConfig) throws Exception {
        if (valuesImportConfig.getFileNameParameters() != null && (valuesImportConfig.getFileNameParameters().get(PREPROCESSOR) != null
                || valuesImportConfig.getFileNameParameters().get(EdBrokingExtension.IMPORT_TEMPLATE) != null)) {
            try {
                File file = new File(AzquoMemoryDB.getGroovyDir() + "/" + valuesImportConfig.getFileNameParameters().get(PREPROCESSOR));
                if (!file.exists()) {
                    file = new File(AzquoMemoryDB.getGroovyDir() + "/" + valuesImportConfig.getFileNameParameters().get(EdBrokingExtension.IMPORT_TEMPLATE));
                }
                if (!file.exists()) {
                    file = new File(AzquoMemoryDB.getGroovyDir() + "/" + valuesImportConfig.getFileNameParameters().get(EdBrokingExtension.IMPORT_TEMPLATE) + ".groovy");
                }
                if (file.exists()) {
                    System.out.println("Groovy found! Running  . . . ");
                    Object[] groovyParams = new Object[1];
                    groovyParams[0] = valuesImportConfig;
                    //groovyParams[1] = valuesImportConfig.getAzquoMemoryDBConnection();
                    GroovyShell shell = new GroovyShell();
                    final Script script = shell.parse(file);
                    System.out.println("loaded groovy " + file.getPath());
                    valuesImportConfig.setFilePath((String) script.invokeMethod("fileProcess", groovyParams));
                }
            } catch (GroovyRuntimeException e) {
                // exception could be because the groovy script didn't have a function that matched
                e.printStackTrace();
                throw new Exception("Groovy error " + valuesImportConfig.getFileName() + ": " + e.getMessage() + "\n");
            }
        }
    }


    private static void checkMaybeHasHeadings(ValuesImportConfig valuesImportConfig) throws IOException {
        if (valuesImportConfig.getImportInterpreter() != null) {
            boolean hasHeadings = false;
            // checks the first few lines of a spreadsheet file to discover evidence of headings.  ten lines without a blank line, and without clauses in the top line indicates no headings
            try (BufferedReader br = Files.newBufferedReader(Paths.get(valuesImportConfig.getFilePath()), Charset.forName("UTF-8"))) { // should it use the charset attribute? todo
                String lineData = br.readLine();
                if (lineData == null || lineData.contains(".") || lineData.contains(";")) {
                    br.close();
                    hasHeadings = true;
                }
                if (!hasHeadings) {
                    int lineCount = 10;
                    while (lineCount-- > 0) {
                        lineData = br.readLine();
                        if (lineData == null || lineData.replace("\t", "").replace(" ", "").length() == 0) {
                            hasHeadings = true;
                            break;
                        }
                    }
                }
            }
            if (!hasHeadings) {
                valuesImportConfig.setSpreadsheet(false);//allow data uploaded in a spreadsheet to use pre-configured headings. isSpreadsheet could also be seen as data with the headings attached
            }
        }
    }

    // Getting some simple info about the file to set up the csv reader and determine batch size
    private static void setLineIteratorAndBatchSize(ValuesImportConfig valuesImportConfig) throws Exception {
        int batchSize = 100_000;
        char delimiter = ',';
        File sizeTest = new File(valuesImportConfig.getFilePath());
        final long fileLength = sizeTest.length();
        try (BufferedReader br = Files.newBufferedReader(Paths.get(valuesImportConfig.getFilePath()), Charset.forName("UTF-8"))) {
            // grab the first line to check on delimiters
            String firstLine = br.readLine();
            String secondLine = null;
            if (firstLine == null || (firstLine.length() == 0 && (secondLine = br.readLine()) == null)) {
                br.close();
                throw new Exception(valuesImportConfig.getFileName() + ": Unable to read any data (perhaps due to an empty file in a zip or an empty sheet in a workbook)");
            }
            if (secondLine == null){ // it might have been assigned above in the empty file check - todo clean logic?
                secondLine = br.readLine();
            }
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
        if (valuesImportConfig.getImportInterpreter() != null && valuesImportConfig.getImportInterpreter().getAttribute(FILEENCODING) != null) {
            valuesImportConfig.setOriginalIterator(csvMapper.readerFor(String[].class).with(schema).readValues(
                    new InputStreamReader(new FileInputStream(valuesImportConfig.getFilePath()), valuesImportConfig.getImportInterpreter().getAttribute(FILEENCODING))));
            // so override file encoding.
        } else {
            valuesImportConfig.setOriginalIterator(csvMapper.readerFor(String[].class).with(schema).readValues(new File(valuesImportConfig.getFilePath())));
        }
        // the copy held in case of the transpose
        valuesImportConfig.setLineIterator(valuesImportConfig.getOriginalIterator());
        valuesImportConfig.setBatchSize(batchSize);
    }

    // todo - can we just get rid of this? I don't think anyone is using it
    private static void checkTranspose(ValuesImportConfig valuesImportConfig) {
        if (valuesImportConfig.getImportInterpreter() != null) {
            if ("true".equalsIgnoreCase(valuesImportConfig.getImportInterpreter().getAttribute("transpose"))) {
                final List<String[]> sourceList = new ArrayList<>();
                // originalIterator will be closed at the end. Worth noting that transposing shouldn't really be done on massive files, I can't imagine it would be
                while (valuesImportConfig.getOriginalIterator().hasNext()) {
                    sourceList.add(valuesImportConfig.getOriginalIterator().next());
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
                    valuesImportConfig.setLineIterator(flipped.iterator());
                }
            }
        }
    }

    private static void checkSkipLinesAndSimpleImportInterpreterHeaders(ValuesImportConfig valuesImportConfig) {
        if (!valuesImportConfig.isSpreadsheet() && valuesImportConfig.getImportInterpreter() != null) {
            String importHeaders = valuesImportConfig.getImportInterpreter().getAttribute(HEADINGSSTRING); // look for headers and parse them if they are there
            if (importHeaders != null) {
                String skipLinesSt = valuesImportConfig.getImportInterpreter().getAttribute(SKIPLINESSTRING);
                if (skipLinesSt != null) {
                    try {
                        valuesImportConfig.setSkipLines(Integer.parseInt(skipLinesSt));
                    } catch (Exception ignored) {
                    }
                }
                System.out.println("has headers " + importHeaders);
                valuesImportConfig.setHeaders(Arrays.asList(importHeaders.split("¬")));// delimiter a bit arbitrary, would like a better solution if I can think of one.
            }
        }
    }

    private static void checkHeadingsOnTheFileAndSkipLines(ValuesImportConfig valuesImportConfig) throws Exception {
        // We might use the headers on the data file, this is notably used when setting up the headers themselves.
        List<String> headers = valuesImportConfig.getHeaders();
        if (headers.size() == 0) {
            headers = new ArrayList<>(Arrays.asList(valuesImportConfig.getLineIterator().next()));
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
                if (!valuesImportConfig.getLineIterator().hasNext()) {
                    throw new Exception("Invalid headers on import file - is this a report that required az_ReportName?");
                }
                // option to stack the clauses vertically
                List<String> oldHeaders = new ArrayList<>(headers);
                if (!buildHeadersFromVerticallyListedClauses(headers, valuesImportConfig.getLineIterator())) {
                    headers = oldHeaders;
                }
            }
            if (valuesImportConfig.isSpreadsheet() && (valuesImportConfig.getFileNameParameters() == null ||valuesImportConfig.getFileNameParameters().get(EdBrokingExtension.IMPORT_TEMPLATE) == null) ) { // it's saying really is it a template (isSpreadsheet = yes)
                // basically if there were no headings in the DB but they were found in the file then put them in the DB to be used by files with the similar names
                // as in add the headings to the first upload then upload again without headings (assuming the file name is the same!)
                Name importSheets = NameService.findOrCreateNameInParent(valuesImportConfig.getAzquoMemoryDBConnection(), ALLIMPORTSHEETS, null, false);
                Name dataImportThis = NameService.findOrCreateNameInParent(valuesImportConfig.getAzquoMemoryDBConnection(),
                        "DataImport " + valuesImportConfig.getFileSource(), importSheets, true);
                StringBuilder sb = new StringBuilder();
                for (String header : headers) {
                    sb.append(header).append("¬");
                }
                dataImportThis.setAttributeWillBePersisted(HEADINGSSTRING, sb.toString());
                dataImportThis.setAttributeWillBePersisted(SKIPLINESSTRING, "1");//  currently assuming one line - may need to adjust
            }
        } else {
            int skipLines = valuesImportConfig.getSkipLines();
            while (skipLines-- > 0) {
                valuesImportConfig.getLineIterator().next();
            }
        }
        valuesImportConfig.setHeaders(headers);
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
                if (heading.length() > 0 && !heading.equals("--")) { //ignore "--", can be used to give space below the headers
                    if (colNo >= headers.size()){
                        headers.add(heading);
                    }else {
                        if (heading.startsWith(".")) {
                            headers.set(colNo, headers.get(colNo) + heading);
                        } else {
                            if (headers.get(colNo).length() == 0) {
                                headers.set(colNo, heading);
                            } else {
                                if (findReservedWord(heading)) {
                                    headers.set(colNo, headers.get(colNo) + ";" + heading.trim());
                                } else {
                                    headers.set(colNo, heading.trim() + "|" + headers.get(colNo));
                                }
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
                || heading.startsWith(HeadingReader.REMOVESPACES)
                || heading.startsWith(HeadingReader.DATELANG)
                || heading.startsWith(HeadingReader.ONLY)
                || heading.startsWith(HeadingReader.EXCLUSIVE)
                || heading.startsWith(HeadingReader.CLEAR)
                || heading.startsWith(HeadingReader.COMMENT)
                || heading.startsWith(HeadingReader.EXISTING)
                || heading.startsWith(HeadingReader.LINEHEADING)
                || heading.startsWith(HeadingReader.LINEDATA)
                || heading.startsWith(HeadingReader.CLASSIFICATION)
                || heading.startsWith(HeadingReader.SPLIT);
    }
}