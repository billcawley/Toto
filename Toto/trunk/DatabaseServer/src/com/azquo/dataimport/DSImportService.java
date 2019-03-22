package com.azquo.dataimport;

import com.azquo.TypedPair;
import com.azquo.memorydb.AzquoMemoryDBConnection;
import com.azquo.memorydb.DatabaseAccessToken;
import com.azquo.memorydb.core.AzquoMemoryDB;
import com.azquo.memorydb.core.Name;
import com.azquo.memorydb.service.NameService;
import com.azquo.spreadsheet.transport.UploadedFile;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvParser;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import groovy.lang.GroovyRuntimeException;
import groovy.lang.GroovyShell;
import groovy.lang.Script;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.MalformedInputException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

/**top
 * Copyright (C) 2018 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
 * <p>
 * Created by Edd on 20/05/15.
 * <p>
 * This class is the entry point for processing an uploaded data file.
 * <p>
 * Azquo has no schema like an SQL database but to load data a basic set structure needs to be defined
 * and rules for interpreting files need to be also. These two together effectively are the equivalent of an SQL schema.
 * Notably the rules for interpreting a file are now often configured in ImportTemplates on the report server.
 */
public class DSImportService {

    // called by RMIImplementation, the entry point from the report server
    public static UploadedFile readPreparedFile(final DatabaseAccessToken databaseAccessToken, UploadedFile uploadedFile, final String user) throws Exception {
        System.out.println("Reading file " + uploadedFile.getPath());
        // we have the temporary db if it's a validation test, this is zapped at the end
        AzquoMemoryDBConnection azquoMemoryDBConnection = uploadedFile.isValidationTest()
                ? AzquoMemoryDBConnection.getTemporaryCopyConnectionFromAccessToken(databaseAccessToken)
                : AzquoMemoryDBConnection.getConnectionFromAccessToken(databaseAccessToken);
        azquoMemoryDBConnection.setProvenance(user, "imported", uploadedFile.getFileNamesAsString(), "");
        // if the provenance is unused I could perhaps zap it but it's not a big deal for the mo
        UploadedFile toReturn = readPreparedFile(azquoMemoryDBConnection, uploadedFile);
        toReturn.setDataModified(!azquoMemoryDBConnection.isUnusedProvenance());
        toReturn.setProvenanceId(azquoMemoryDBConnection.getProvenance().getId());
        return toReturn;
    }
    // Called by above but also directly from SSpreadsheet service when it has prepared a CSV from data entered ad-hoc into a sheet
    public static UploadedFile readPreparedFile(AzquoMemoryDBConnection azquoMemoryDBConnection, UploadedFile uploadedFile) throws Exception {
        // ok the thing he is to check if the memory db object lock is free, more specifically don't start an import if persisting is going on, since persisting never calls import there should be no chance of a deadlock from this
        // of course this doesn't currently stop the opposite, a persist being started while an import is going on.
        azquoMemoryDBConnection.addToUserLog("Reading " + uploadedFile.getFullFileName());
        azquoMemoryDBConnection.lockTest();
        azquoMemoryDBConnection.getAzquoMemoryDB().clearCaches();
        if (uploadedFile.getFileName().toLowerCase().startsWith("sets")) { // typically from a sheet with that name in a book
            // not currently paying attention to isSpreadsheet - only possible issue is the replacing of \\\n with \n required based off writeCell in ImportFileUtilities
            SetsImport.setsImport(azquoMemoryDBConnection, uploadedFile);
        } else {
            // needs the groovy file specified correctly
            try {
                File file = new File(AzquoMemoryDB.getGroovyDir() + "/" + uploadedFile.getAdditionalDataProcessor());
                if (file.exists()) {
                    System.out.println("Groovy found! Running  . . . ");
                    Object[] groovyParams = new Object[2];
                    groovyParams[0] = uploadedFile;
                    groovyParams[1] = azquoMemoryDBConnection;
                    GroovyShell shell = new GroovyShell();
                    final Script script = shell.parse(file);
                    System.out.println("loaded groovy " + file.getPath());
                    // overly wordy way to override the path
                    uploadedFile.setPath((String) script.invokeMethod("fileProcess", groovyParams));
                }
            } catch (GroovyRuntimeException e) {
                // exception could be because the groovy script didn't have a function that matched
                e.printStackTrace();
                throw new Exception("Groovy error " + uploadedFile.getFileName() + ": " + e.getMessage() + "\n");
            }


            // checks the first few lines to sort batch size and get a hopefully correctly configured line iterator. Nothing to do with heading interpretation
            int batchSize = 100_000;
            char delimiter = ',';
            final File sizeTest = new File(uploadedFile.getPath());
            final long fileLength = sizeTest.length();
            try (BufferedReader br = Files.newBufferedReader(Paths.get(uploadedFile.getPath()), Charset.forName("UTF-8"))) {
                // grab the first line to check on delimiters
                try {
                    String firstLine = br.readLine();
                    String secondLine = null;
                    if (firstLine == null || (firstLine.length() == 0 && (secondLine = br.readLine()) == null)) {
                        br.close();
                        throw new Exception(uploadedFile.getFileName() + ": Unable to read any data (perhaps due to an empty file in a zip or an empty sheet in a workbook)");
                    }
                    if (secondLine == null) { // it might have been assigned above in the empty file check - todo clean logic?
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
                } catch (MalformedInputException e){
                    throw new Exception(uploadedFile.getFileName() + ": Unable to read any data (perhaps due to an empty file in a zip or an empty sheet in a workbook)");
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
            MappingIterator<String[]> lineIterator;
            if (uploadedFile.getFileEncoding() != null) {
                // so override file encoding.
                lineIterator = csvMapper.readerFor(String[].class).with(schema).readValues(new InputStreamReader(new FileInputStream(uploadedFile.getPath()), uploadedFile.getFileEncoding()));
            } else {
                lineIterator = (csvMapper.readerFor(String[].class).with(schema).readValues(new File(uploadedFile.getPath())));
            }

            // read headings off file - under new logic this needs to pay attention to what was passed from the report server
            int skipLines = uploadedFile.getSkipLines();
            // things might be shifted to the right which would break top headings, hack in support for this by having a
            // list of top headings as they appear at offsets up to a limit as well as normal
            int offsetLimit = 20;
            List<Map<String, String>> topHeadingsValuesByOffset = new ArrayList<>();
            for (int offset = 0; offset < offsetLimit; offset++){
                topHeadingsValuesByOffset.add(new HashMap<>());
            }
            /*
            Two different criteria - without quotes it just wants a match - is that cell there with that value?
            With quotes the value in that cell will be saved against that key to be used in heading clauses

            Coverholder:
            `Coverholder name`
            */


            for (int rowIndex = 0; rowIndex < skipLines; rowIndex++) {
                String[] row = lineIterator.next();
                if (uploadedFile.getTopHeadings() != null) {
                    for (int colIndex = 0; colIndex < row.length; colIndex++) {
                        // as mentioned above, run through a bunch of offests here in case the topheadings have been shifted to the right - note they still have to have the same configuration
                        for (int offset = 0; offset < offsetLimit; offset++){
                            String topHeading = uploadedFile.getTopHeadings().get(new TypedPair<>(rowIndex, colIndex - offset)); // yes can be a negative col at least at first - no harm it just won't find anything
                            if (topHeading != null && row[colIndex].length() > 0) { // we found the cell
                                if (topHeading.startsWith("`") && topHeading.endsWith("`")) { // we grab the value and store it
                                    topHeadingsValuesByOffset.get(offset).put(topHeading.replace("`", ""), row[colIndex]);
                                } else if (row[colIndex].equalsIgnoreCase(topHeading)) { // I need to check it matches
                                    topHeadingsValuesByOffset.get(offset).put(topHeading, "FOUND"); // just something
                                }
                            }
                        }
                    }
                }
            }

            // most of the time the first will have map entries and none of the rest will but it's possible I suppose. Get the most complete set and exception if it's not actually complete
            Map<String, String> topHeadingsValues = new HashMap<>();
            for (int offset = 0; offset < offsetLimit; offset++){
                if (topHeadingsValuesByOffset.get(offset).size() > topHeadingsValues.size()){
                    if (offset > 0){
                        System.out.println("Using offset on topheadings : " + offset);
                    }
                    topHeadingsValues = topHeadingsValuesByOffset.get(offset);
                }
            }


            if (uploadedFile.getTopHeadings() != null && uploadedFile.getTopHeadings().size() != topHeadingsValues.size()) {
                throw new Exception("Top headings expected : " + uploadedFile.getTopHeadings().values() + " top headings found " + topHeadingsValues.keySet());
            }
            List<String> headings = new ArrayList<>();
            // to lookup composite columns based on the file heading
            final Map<String, Integer> fileHeadingCompositeLookup = new HashMap<>();
            // to lookup composite columns based on interim "ModelHeadings" heading names - interim if a "mode" was used e.g. Hiscox on the risk import template
            final Map<String, Integer> interimCompositeLookup = new HashMap<>();
            // to lookup composite columns based on Azquo definitions - the standard old way, looking up what's before the first ;
            // but it will NOT do the dot to "; attribute" replace before so you could reference Shop.Address1 in composite
            Map<String, Integer> azquoHeadingCompositeLookup = new HashMap<>();
            int lastColumnToActuallyRead = 0;
            if (uploadedFile.getSimpleHeadings() == null) { // if there were simple headings then we read NO headings off the file
                if (uploadedFile.getHeadingsByFileHeadingsWithInterimLookup() != null) { // then we need to find the headings specified
                    int headingDepth = 1;
                    for (List<String> headingsToFind : uploadedFile.getHeadingsByFileHeadingsWithInterimLookup().keySet()) {
                        if (headingsToFind.size() > headingDepth) {
                            headingDepth = headingsToFind.size();
                        }
                    }
                    // so try and find the headings
                    List<List<String>> headingsFromTheFile = new ArrayList<>();
                    for (int i = 0; i < headingDepth; i++) {
                        if (lineIterator.hasNext()) {
                            String[] lineCells = lineIterator.next();
                            for (int j = 0; j < lineCells.length; j++) {
                                if (i == 0) {
                                    headingsFromTheFile.add(new ArrayList<>());
                                }
                                String lineHeading = lineCells[j].replace("\\\\n", "\n").replace("\\\\t", "\t").trim();
                                if (!lineHeading.isEmpty()){ // not having blanks
                                    headingsFromTheFile.get(j).add(lineHeading);
                                }
                            }
                        } else {
                            throw new Exception("Unable to find expected headings on the file");
                        }
                    }
                    // for feedback to the user
                    uploadedFile.setFileHeadings(headingsFromTheFile);

                    // ok now we get to the actual lookup
                    // copy it as we're going to remove things - need to leave the original intact as it's part of feedback to the user
                    Map<List<String>, TypedPair<String, String>> headingsByLookupCopy = new HashMap<>(uploadedFile.getHeadingsByFileHeadingsWithInterimLookup());
                    int currentFileCol = 0;
                    for (List<String> headingsForAColumn : headingsFromTheFile) {// generally headingsForAColumn will just just have one element
                        if (headingsByLookupCopy.get(headingsForAColumn) != null) {
                            lastColumnToActuallyRead = currentFileCol;
                            TypedPair<String, String> removed = headingsByLookupCopy.remove(headingsForAColumn);// take the used one out - after running through the file we need to add the remainder on to the end
                            headings.add(removed.getFirst());
                            if (removed.getSecond() != null) {
                                interimCompositeLookup.put(removed.getSecond().toUpperCase(), headings.size() - 1);
                            }
                        } else {
                            headings.add("");
                        }
                        // ok the last of the headings that has a value is the one we want for the fileHeadingCompositeLookup
                        // going to allow composite lookup on all headings whether they're used as a proper heading or not
                        Collections.reverse(headingsForAColumn);
                        for (String heading : headingsForAColumn) {
                            if (!heading.isEmpty()) {
                                fileHeadingCompositeLookup.put(heading.toUpperCase(), headings.size() - 1);
                                break;
                            }
                        }
                        currentFileCol++;
                    }
                    List<TypedPair<String, String>> headingsNoFileHeadingsWithInterimLookup = uploadedFile.getHeadingsNoFileHeadingsWithInterimLookup() != null ? uploadedFile.getHeadingsNoFileHeadingsWithInterimLookup() : new ArrayList<>();
                    headingsNoFileHeadingsWithInterimLookup.addAll(headingsByLookupCopy.values());
                    // ok now we add the headings with no file headings or where the file headings couldn't be found
                    for (TypedPair<String, String> leftOver : headingsNoFileHeadingsWithInterimLookup) {
                        // pasted and modified from old code to exeption on required as necessary
                        String headingToAdd = leftOver.getFirst();

                        // most of the time top headings will be jammed in below as a simple default but that default might be against an existing heading in which case
                        // add the default here and knock it off the list so it's not added again later
                        // note - if a default top heading matched a heading on file the default won't get set
                        if (!topHeadingsValues.isEmpty()){
                            String toCheckAgainstTopHeadings = leftOver.getFirst();
                            if (toCheckAgainstTopHeadings.contains(";")){
                                toCheckAgainstTopHeadings = toCheckAgainstTopHeadings.substring(0, toCheckAgainstTopHeadings.indexOf(";"));
                                if (topHeadingsValues.containsKey(toCheckAgainstTopHeadings) && !"FOUND".equals(topHeadingsValues.get(toCheckAgainstTopHeadings))){
                                    headingToAdd = leftOver.getFirst() + ";default " + topHeadingsValues.remove(toCheckAgainstTopHeadings);
                                }
                            }
                        }

                        String[] clauses = headingToAdd.split(";");
                        for (String clause : clauses) {
                            if (clause.toLowerCase().startsWith("required")) {
                            /*check both the general and specific import attributes
                            so it is required and not composition or top heading and it doesn't have a default value
                            *then* we exception. Note that this isn't talking about the value on a line it's asking if the heading itself exists
                            so a problem in here is a heading config problem I think rather than a data problem */
                                if (!headingToAdd.toLowerCase().contains(HeadingReader.DEFAULT)
                                        && !leftOver.getFirst().toLowerCase().contains(HeadingReader.COMPOSITION)
                                        && uploadedFile.getParameter(clauses[0].trim().toLowerCase()) == null
                                ) {
                                    throw new Exception("headings missing required heading: " + leftOver.getFirst());
                                }
                                break;
                            }
                        }

                        headings.add(headingToAdd);
                        if (leftOver.getSecond() != null) {
                            interimCompositeLookup.put(leftOver.getSecond().toUpperCase(), headings.size() - 1);
                        }
                    }
                } else { // both null, we want azquo import stuff directly off the file, it might be multi level clauses
                    // the straight pull off the first line
                    headings = new ArrayList<>(Arrays.asList(lineIterator.next()));
                    // older code pasted, could be tidied? Todo
                    boolean hasClauses = false;
                    boolean blank = true;
                    for (String heading : headings) {
                        if (!heading.isEmpty()){
                            blank = false;
                        }
                        if (heading.contains(".") || heading.contains(";")) {
                            hasClauses = true;
                        }
                    }
                    if (blank){
                        uploadedFile.setError("Blank first line and no top headings, not loading.");
                        return uploadedFile;
                    }

                    // no clauses, try to find more
                    if (!hasClauses) {
                        if (!lineIterator.hasNext()) {
                            throw new Exception("Invalid headings on import file - is this a report that required az_ReportName?");
                        }
                        // option to stack the clauses vertically - we are allowing this on annotation, will be useful on set up files
                        // Todo - inline this and then zap ValuesImportConfigProcessor
                        if (!ValuesImportConfigProcessor.buildHeadingsFromVerticallyListedClauses(headings, lineIterator)) {
                            uploadedFile.setError("Unable to find suitable stacked headings.");
                            return uploadedFile;
                        }
                    }
                }
            } else { // straight simple headings
                headings = uploadedFile.getSimpleHeadings();
            }
            // zap any trailing empty headings, can be an issue with the vertically built headings
            while (!headings.isEmpty() && headings.get(headings.size() - 1).isEmpty()){
                headings.remove(headings.size() - 1);
            }
            if (lastColumnToActuallyRead == 0){
                lastColumnToActuallyRead = headings.size() - 1; // default to that
            }
            // now add top headings that have a value
            for (String headingName : topHeadingsValues.keySet()) {
                if (!topHeadingsValues.get(headingName).equals("FOUND")) {
                    headings.add(headingName + ";default " + topHeadingsValues.get(headingName));
                }
            }

            // now sort the standard composite map, while we're here can do assumptions too from parameters
            // these will want the "pre . replaced with ; attribute" reference to a heading also I think, want to be able to say shop.address
            int index = 0;
            for (String heading : headings) {
                String headingNameForLookUp = heading.contains(";") ? heading.substring(0,heading.indexOf(";")).trim() : heading.trim();
                azquoHeadingCompositeLookup.put(headingNameForLookUp.toUpperCase(), index);
                String assumption = uploadedFile.getParameter(headingNameForLookUp);
                if (assumption != null) {
                    headings.set(index, heading + ";override " + assumption);
                }
                index++;
            }

            CompositeIndexResolver compositeIndexResolver = new CompositeIndexResolver(fileHeadingCompositeLookup, interimCompositeLookup, azquoHeadingCompositeLookup);
    /*

    deal with attribute short hand and pivot stuff, essentially pre processing that can be done before making any MutableImportHeadings

    */

            String lastHeading = "";
            boolean pivot = false;
            for (int i = 0; i < headings.size(); i++) {
                String heading = headings.get(i);
                if (heading.trim().length() > 0) {
                    // attribute headings can start with . shorthand for the last heading followed by .
                    // of course starting the first heading with a . causes a problem here but it would make no sense to do so!
                    if (heading.startsWith(".")) {
                        heading = lastHeading + heading;
                    } else {
                        if (heading.contains(";")) {
                            lastHeading = heading.substring(0, heading.indexOf(";"));
                        } else {
                            lastHeading = heading;
                        }
                    }
                    //treat 'a.b' as 'a;attribute b'  e.g.   london.DEFAULT_DISPLAY_NAME
                    // ok, the old heading.replace heading.replace(".", ";attribute ") is no good, need to only replace outside of string literals
                    boolean inStringLiteral = false;
                    // and now composite can have . between the literals so watch for that too. Writing parsers is a pain,
                    boolean inComposition = false;
                    StringBuilder replacedHeading = new StringBuilder();
                    for (char c : heading.toCharArray()){
                        if (replacedHeading.toString().toLowerCase().endsWith("composition")){
                            inComposition = true;
                        }
                        if (inComposition && !inStringLiteral && c == ';'){
                            inComposition = false;
                        }
                        if (c == '`'){
                            inStringLiteral = !inStringLiteral;
                        }
                        if (c == '.' && !inStringLiteral && !inComposition){
                            replacedHeading.append(";attribute ");
                        } else {
                            replacedHeading.append(c);
                        }
                    }
                    heading = replacedHeading.toString();
                /* line heading and data
                Line heading means that the cell data on the line will be a name that is a parent of the line no
                Line data means that the cell data on the line will be a value which is attached to the line number name (for when there's not for example an order reference to be a name on the line)
                Line heading and data essentially are shorthand, this expands them and creates supporting names for the pivot if necessary.
                As the boolean shows, pivot stuff
                */
                    if (heading.contains(HeadingReader.LINEHEADING) && heading.indexOf(";") > 0) {
                        pivot = true;
                        String headname = heading.substring(0, heading.indexOf(";"));
                        Name headset = NameService.findOrCreateNameInParent(azquoMemoryDBConnection, "All headings", null, false);
                        // create the set the line heading name will go in
                        // note - headings in different import files will be considered the same if they have the same name
                        NameService.findOrCreateNameInParent(azquoMemoryDBConnection, headname.replace("_", " "), headset, true);
                        heading = heading.replace(HeadingReader.LINEHEADING, "parent of LINENO;child of " + headname.replace("_", " ") + ";language " + headname);
                    }

                    // using file name instead of import interpreter name, not sure of the best plan there

                    if (heading.contains(HeadingReader.LINEDATA) && heading.indexOf(";") > 0) {
                        pivot = true;
                        String headname = heading.substring(0, heading.indexOf(";"));
                        Name alldataset = NameService.findOrCreateNameInParent(azquoMemoryDBConnection, "All data", null, false);
                        Name thisDataSet = NameService.findOrCreateNameInParent(azquoMemoryDBConnection, uploadedFile.getFileName() + " data", alldataset, false);
                        // create the set the line data name will go in
                        NameService.findOrCreateNameInParent(azquoMemoryDBConnection, headname.replace("_", " "), thisDataSet, false);
                        heading = heading.replace(HeadingReader.LINEDATA, "peers {LINENO}").replace("_", " ");
                    }
                }
                headings.set(i, heading);
            }
            if (pivot) {
                Name allLines = NameService.findOrCreateNameInParent(azquoMemoryDBConnection, "All lines", null, false);
                // create the name based on this file name where we put the names generated to deal with pivot tables. Note this means uploading a file with the same name and different data causes havok!
                NameService.findOrCreateNameInParent(azquoMemoryDBConnection, uploadedFile.getFileName() + " lines", allLines, false);
                headings.add("LINENO;composition LINENO;language " + uploadedFile.getFileName() + ";child of " + uploadedFile.getFileName() + " lines|"); // pipe on the end, clear context if there was any
            }

            // ok so now these headings *should* be done and ready for parsing in the heading reader
            List<ImmutableImportHeading> immutableImportHeadings = HeadingReader.readHeadings(azquoMemoryDBConnection, uploadedFile, headings);
            // when it is done we assume we're ready to batch up lines with headings and import with BatchImporter
            ValuesImport.valuesImport(azquoMemoryDBConnection
                    , lineIterator
                    , uploadedFile
                    , immutableImportHeadings
                    , batchSize
                    , lastColumnToActuallyRead, compositeIndexResolver);
        }
        return uploadedFile; // it will (should!) have been modified
    }

    // top chunk pasted from above and modified, check factoring. Todo

    // return line number, the thing we were looking for and the line
    public static Map<Integer, TypedPair<String, String>> getLinesWithValuesInColumn(UploadedFile uploadedFile, int columnIndex, Set<String> valuesToCheck) throws IOException {
        char delimiter = ',';
//        System.out.println("get lines with values and column, col index : " + columnIndex);
//        System.out.println("get lines with values and column, values to check : " + valuesToCheck);
        try (BufferedReader br = Files.newBufferedReader(Paths.get(uploadedFile.getPath()), Charset.forName("UTF-8"))) {
            // grab the first line to check on delimiters
                String firstLine = br.readLine();
                if (firstLine.contains("|")) {
                    delimiter = '|';
                }
                if (firstLine.contains("\t")) {
                    delimiter = '\t';
                }
        }
        CsvMapper csvMapper = new CsvMapper();
        csvMapper.enable(CsvParser.Feature.WRAP_AS_ARRAY);
        CsvSchema schema = csvMapper.schemaFor(String[].class)
                .withColumnSeparator(delimiter)
                .withLineSeparator("\n");
        if (delimiter == '\t') {
            schema = schema.withoutQuoteChar();
        }
        MappingIterator<String[]> lineIterator;
        if (uploadedFile.getFileEncoding() != null) {
            // so override file encoding.
            lineIterator = csvMapper.readerFor(String[].class).with(schema).readValues(new InputStreamReader(new FileInputStream(uploadedFile.getPath()), uploadedFile.getFileEncoding()));
        } else {
            lineIterator = (csvMapper.readerFor(String[].class).with(schema).readValues(new File(uploadedFile.getPath())));
        }
        Map<Integer, TypedPair<String, String>> toReturn = new HashMap<>();
        // ok, run the check
        while (lineIterator.hasNext()){
            String[] row = lineIterator.next();
            // will make it case insensetive
            if (columnIndex < row.length && valuesToCheck.contains(row[columnIndex].toLowerCase())){
                StringBuilder sb = new StringBuilder();
                // rebuild the line, seems a little stupid :P
                for (String s : row) {
                    if (sb.length() > 0) {
                        sb.append(delimiter);
                    }
                    sb.append(s);
                }
                toReturn.put(lineIterator.getCurrentLocation().getLineNr() - 1, new TypedPair<>(row[columnIndex], sb.toString()));
            }
        }
        return toReturn;
    }
}