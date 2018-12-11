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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

/**
 * Copyright (C) 2018 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
 * <p>
 * Created by Edd on 20/05/15.
 * <p>
 * This class is the entry point for processing an uploaded data file.
 * <p>
 * Azquo has no schema like an SQL database but to load data a basic set structure needs to be defined
 * and rules for interpreting files need to be also. These two together effectively are the equivalent of an SQL schema.
 */
public class DSImportService {

    // called by RMIImplementation, the entry point from the report server
    public static UploadedFile readPreparedFile(final DatabaseAccessToken databaseAccessToken, UploadedFile uploadedFile, final String user) throws Exception {
        System.out.println("Reading file " + uploadedFile.getPath());
        AzquoMemoryDBConnection azquoMemoryDBConnection = AzquoMemoryDBConnection.getConnectionFromAccessToken(databaseAccessToken);
        azquoMemoryDBConnection.setProvenance(user, "imported", uploadedFile.getFileNamessAsString(), "");
        // if the provenance is unused I could perhaps zap it but it's not a big deal for the mo
        UploadedFile toReturn = readPreparedFile(azquoMemoryDBConnection, uploadedFile);
        toReturn.setDataModified(!azquoMemoryDBConnection.isUnusedProvenance());
        return toReturn;
    }

    // Called by above but also directly from SSpreadsheet service when it has prepared a CSV from data entered ad-hoc into a sheet
    public static UploadedFile readPreparedFile(AzquoMemoryDBConnection azquoMemoryDBConnection, UploadedFile uploadedFile) throws Exception {
        // ok the thing he is to check if the memory db object lock is free, more specifically don't start an import if persisting is going on, since persisting never calls import there should be no chance of a deadlock from this
        // of course this doesn't currently stop the opposite, a persist being started while an import is going on.
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
            File sizeTest = new File(uploadedFile.getPath());
            final long fileLength = sizeTest.length();
            try (BufferedReader br = Files.newBufferedReader(Paths.get(uploadedFile.getPath()), Charset.forName("UTF-8"))) {
                // grab the first line to check on delimiters
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
                lineIterator = csvMapper.readerFor(String[].class).with(schema).readValues(new InputStreamReader(new FileInputStream(uploadedFile.getPath()), uploadedFile.getFileEncoding()));
                // so override file encoding.
            } else {
                lineIterator = (csvMapper.readerFor(String[].class).with(schema).readValues(new File(uploadedFile.getPath())));
            }



            // read headings off file - under new logic this needs to pay attention to what was passed from the report server
            int skipLines = uploadedFile.getSkipLines();
            Map<String, String> topHeadingsValues = new HashMap<>();
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
                        String topHeading = uploadedFile.getTopHeadings().get(new TypedPair<>(rowIndex, colIndex));
                        if (topHeading != null && row[colIndex].length() > 0) { // we found the cell
                            if (topHeading.startsWith("`") && topHeading.endsWith("`")) { // we grab the value and store it
                                topHeadingsValues.put(topHeading, row[colIndex]);
                            } else if (row[colIndex].equalsIgnoreCase(topHeading)) { // I need to check it matches
                                topHeadingsValues.put(topHeading, "FOUND"); // just something
                            }
                        }
                    }
                }
            }
            if (uploadedFile.getTopHeadings() != null && uploadedFile.getTopHeadings().size() != topHeadingsValues.size()) {
                throw new Exception("Top headings expected : " + uploadedFile.getTopHeadings().values() + " top headings found " + topHeadingsValues.keySet());
            }
            List<String> headings = new ArrayList<>();
            // to lookup composite columns based on the file heading
            Map<String, Integer> fileHeadingCompositeLookup = new HashMap<>();
            // to lookup composite columns based on interim "ModelHeadings" heading names - interim if a "mode" was used e.g. Hiscox on the risk import template
            Map<String, Integer> interimCompositeLookup = new HashMap<>();
            // to lookup composite columns based on Azquo definitions - the standard old way, looking up what's before the first ; but it will NOT do the dot to "; attribute" replace before
            // so you could reference Shop.Address1 in composite
            Map<String, Integer> azquoHeadingCompositeLookup = new HashMap<>();
            if (uploadedFile.getSimpleHeadings() == null) { // if there were simple headings then we read NO headings off the file
                if (uploadedFile.getHeadingsByFileHeadingsWithInterimLookup() != null) { // then we need to fund the headings specified
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
                                headingsFromTheFile.get(j).add(lineCells[j].replace("\\\\n", "\n").replace("\\\\t", "\t")); // note - jamming blank ones in here
                            }
                        } else {
                            throw new Exception("Unable to find expected headings on the file");
                        }
                    }
                    // ok now we get to the actual lookup
                    Map<List<String>, TypedPair<String, String>> headingsByLookupCopy = new HashMap<>(uploadedFile.getHeadingsByFileHeadingsWithInterimLookup()); // copy it as we're going to remove things
                    for (List<String> headingsForAColumn : headingsFromTheFile) {// generally headingsForAColumn will just just have one element
                        if (headingsByLookupCopy.get(headingsForAColumn) != null) {
                            TypedPair<String, String> removed = headingsByLookupCopy.remove(headingsForAColumn);// take the used one out - after runnning through the file we need to add the remainder on to the end
                            headings.add(removed.getFirst());
                            // ok the last of the headings that has a value is the one we want for the fileHeadingCompositeLookup
                            Collections.reverse(headingsForAColumn);
                            for (String heading : headingsForAColumn) {
                                if (!heading.isEmpty()) {
                                    fileHeadingCompositeLookup.put(heading.toUpperCase(), headings.size() - 1);
                                    break;
                                }
                            }
                            if (removed.getSecond() != null) {
                                interimCompositeLookup.put(removed.getSecond().toUpperCase(), headings.size() - 1);
                            }
                        } else {
                            headings.add("");
                        }
                    }
                    List<TypedPair<String, String>> headingsNoFileHeadingsWithInterimLookup = uploadedFile.getHeadingsNoFileHeadingsWithInterimLookup() != null ? uploadedFile.getHeadingsNoFileHeadingsWithInterimLookup() : new ArrayList<>();
                    headingsNoFileHeadingsWithInterimLookup.addAll(headingsByLookupCopy.values());
                    // ok now we add the headings with no file headings or where the file headings couldn't be found
                    for (TypedPair<String, String> leftOver : headingsNoFileHeadingsWithInterimLookup) {
                        // pasted and modified from old code to exeption on required as necessary
                        String[] clauses = leftOver.getFirst().split(";");
                        for (String clause : clauses) {
                            if (clause.toLowerCase().startsWith("required")) {
                            /*check both the general and specific import attributes
                            so it is required and not composition or top heading and it doesn't have a default value
                            *then* we exception. Note that this isn't talking about the value on a line it's asking if the heading itself exists
                            so a problem in here is a heading config problem I think rather than a data problem */
                                if (!leftOver.getFirst().toLowerCase().contains(HeadingReader.DEFAULT)
                                        && !leftOver.getFirst().toLowerCase().contains(HeadingReader.COMPOSITION)
                                        && uploadedFile.getParameter(clauses[0].trim().toLowerCase()) == null
                                ) {
                                    throw new Exception("headings missing required heading: " + leftOver.getFirst());
                                }
                                break;
                            }
                        }
                        headings.add(leftOver.getFirst());
                        if (leftOver.getSecond() != null) {
                            interimCompositeLookup.put(leftOver.getSecond().toUpperCase(), headings.size() - 1);
                        }
                    }
                } else { // both null, we want azquo import stuff directly off the file, it might be multi level clauses
                    // the straight pull off the first line
                    headings = new ArrayList<>(Arrays.asList(lineIterator.next()));
                    // older code pasted, could be tidied? Todo
                    boolean hasClauses = false;
                    for (String heading : headings) {
                        if (heading.contains(".") || heading.contains(";")) {
                            hasClauses = true;
                        }
                    }
                    // no clauses, try to find more
                    if (!hasClauses) {
                        if (!lineIterator.hasNext()) {
                            throw new Exception("Invalid headings on import file - is this a report that required az_ReportName?");
                        }
                        // option to stack the clauses vertically - we are allowing this on annotation, will be useful on set up files
                        // keep a copy as the code internally mangles what's passed. This could be fixed. Todo - inline this and then zap ValuesImportConfigProcessor
                        List<String> headingsCopy = new ArrayList<>(headings);
                        if (!ValuesImportConfigProcessor.buildHeadingsFromVerticallyListedClauses(headings, lineIterator)) {
                            headings = headingsCopy;
                        }
                    }
                }
            } else { // straight simple headings
                headings = uploadedFile.getSimpleHeadings();
            }

            // currently, to my knowledge, the top headings are the only extras.
            // do before composite lookup - may well be used there
            int headingsSizeBeforeExtras = headings.size();
            // now add top headings that have a value
            for (String headingName : topHeadingsValues.keySet()) {
                if (!topHeadingsValues.get(headingName).equals("FOUND")) {
                    headings.add(headingName + ";default " + topHeadingsValues.get(headingName));
                }
            }

            // now sort the standard composite map, while we're here can do assumptoions too from parameters
            // these will want the "pre . replaced with ; attribute" reference to a heading also I think, want to be able to say shop.address
            int index = 0;
            for (String heading : headings) {
                String headingNameForLookUp = heading.contains(";") ? heading.substring(heading.indexOf(";")).trim() : heading.trim();
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
                    heading = heading.replace(".", ";attribute ");//treat 'a.b' as 'a;attribute b'  e.g.   london.DEFAULT_DISPLAY_NAME
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
                    , headingsSizeBeforeExtras, compositeIndexResolver);
        }
        return uploadedFile; // it will (should!) have been modified
    }
}