package com.azquo.dataimport;

import com.azquo.StringLiterals;
import com.azquo.ThreadPools;
import com.azquo.memorydb.AzquoMemoryDBConnection;
import com.azquo.memorydb.core.Name;
import com.azquo.spreadsheet.transport.UploadedFile;
import com.fasterxml.jackson.databind.MappingIterator;

import java.io.BufferedReader;
import java.io.File;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/*
This class batches up data to be loaded doing simple checks on cell values
The actual loading and complex heading resolution should be done in other classes,

 */
class ValuesImport {

    static void valuesImport(AzquoMemoryDBConnection connection, MappingIterator<String[]> lineIterator
            , UploadedFile uploadedFile, List<ImmutableImportHeading> importHeadings
            , int batchSize, int lastColumnToActuallyRead, CompositeIndexResolver compositeIndexResolver) {
        ArrayList<UploadedFile.RejectedLine> rejectedLinesForUserFeedback = new ArrayList<>();
        int lineNo = 0; // out here now as we're trying to continue if lineIterator.next() throws an exception
        try {
            long track = System.currentTimeMillis();
            // now, since this will be multi threaded need to make line objects to batch up. Cannot be completely immutable due to the current logic e.g. composite values
            ArrayList<LineDataWithLineNumber> linesBatched = new ArrayList<>(batchSize);
            List<Future<?>> futureBatches = new ArrayList<>();
            // Local cache of names just to speed things up, a name could be referenced millions of times in one file
            final Map<String, Name> namesFoundCache = new ConcurrentHashMap<>();
            // new format. Line number, the line itself and then a list of errors
            final Map<Integer, List<String>> linesRejected = new ConcurrentHashMap<>(); // track line numbers rejected
            final AtomicInteger noLinesRejected = new AtomicInteger(); // track no lines rejected
            int linesImported = 0; // just for some feedback at the end
            while (lineIterator.hasNext()) linesLoop:{ // the main line reading loop. Unfortunately it can error here
                lineNo++; // we are now on line number 1, line 0 wouldn't mean anything for user feedback
                boolean corrupt = false;
                String[] lineValues;
                try {
                    lineValues = lineIterator.next();
                    // I'm currently working on the principle that the line no is the current location -1
                    // while rather than if as rejected lines could well be sequential!
                    // when validating lines can be skipped according to the user
                    while (uploadedFile.getIgnoreLines() != null && uploadedFile.getIgnoreLines().containsKey(lineIterator.getCurrentLocation().getLineNr() - 1)) {
                        StringBuilder sb = new StringBuilder();
                        sb.append(StringLiterals.DELIBERATELYSKIPPINGLINE).append(lineIterator.getCurrentLocation().getLineNr() - 1).append(", ");
                        for (String cell : lineValues) {
                            sb.append("\t").append(cell);
                        }
                        System.out.println(sb.toString());
                        uploadedFile.getIgnoreLinesValues().put(lineIterator.getCurrentLocation().getLineNr() - 1, sb.toString());
                        // break the outer loop if we've run out of lines . . .
                        if (!lineIterator.hasNext()){
                            break linesLoop;
                        }
                        lineValues = lineIterator.next();
                    }


                    linesImported++;
                    List<ImportCellWithHeading> importCellsWithHeading = new ArrayList<>();
                    int columnIndex = 0;
                    boolean blankLine = true;
                /* essentially run some basic checks on the data as it comes in and batch it up into headings with line values.
                that pairing is very helpful in the BatchImporter
                */
                    int cellCount = 0;
                    for (ImmutableImportHeading immutableImportHeading : importHeadings) {
                        // Intern may save a little memory if strings are repeated a lot. Column Index could point past line values for things like composite.
                        String lineValue = (cellCount++ <= lastColumnToActuallyRead && columnIndex < lineValues.length) ? lineValues[columnIndex].trim().intern() : "";
                        if (lineValue.length() > 0) blankLine = false;
                        if (lineValue.equals("\"")) {// was a problem, might be worth checking if it is still
                            corrupt = true;
                            break;
                        }
                        if (lineValue.startsWith("\"") && lineValue.endsWith("\"")) {
                            lineValue = lineValue.substring(1, lineValue.length() - 1).replace("\"\"", "\"");//strip spurious quote marks inserted by Excel
                        }
                        if (lineValue.startsWith("'") && lineValue.indexOf("'", 1) < 0) {
                            lineValue = lineValue.substring(1);//in Excel insertion of an initial ' means it is a string.
                        }
                        // if generated from a spreadsheet this is a danger, fix now before any interpreting
                        //.replace("\n", "\\\\n").replace("\t", "\\\\t") is what that function did on the report server.
                        if (uploadedFile.isConvertedFromWorksheet()) {
                            lineValue = lineValue.replace("\\\\t", "\t").replace("\\\\n", "\n");
                        }
                        lineValue = checkNumeric(lineValue);
                        // we might have extra data in the file we're not interested in underneath composite columns, if so blank the cell, we want composite to be working in that case - that's what noFileHeading does. Could put above?
                        importCellsWithHeading.add(new ImportCellWithHeading(immutableImportHeading, immutableImportHeading.noFileHeading ? "" : lineValue));
                        columnIndex++;
                    }
                    if (!corrupt && !blankLine) {
                        linesBatched.add(new LineDataWithLineNumber(importCellsWithHeading, lineIterator.getCurrentLocation().getLineNr() - 1)); // line no - 1 as we want where it was, not where it's waiting now
                        // Start processing this batch. As the file is read the active threads will rack up to the maximum number allowed rather than starting at max. Store the futures to confirm all are done after all lines are read.
                        // batch size is derived by getLineIteratorAndBatchSize
                        if (linesBatched.size() == batchSize) {
                            futureBatches.add(ThreadPools.getMainThreadPool().submit(
                                    new BatchImporter(connection
                                            , linesBatched
                                            , namesFoundCache
                                            , linesRejected
                                            , noLinesRejected
                                            , uploadedFile
                                            , compositeIndexResolver))// line no should be the start

                            );
                            linesBatched = new ArrayList<>(batchSize);
                        }
                    } else {
                        linesImported--; // drop it down as we're not even going to try that line
                    }
                } catch (Exception e) {
                    rejectedLinesForUserFeedback.add(new UploadedFile.RejectedLine(lineNo, "", e.getMessage()));
                }
            }
            futureBatches.add(ThreadPools.getMainThreadPool().submit(new BatchImporter(connection
                    , linesBatched
                    , namesFoundCache
                    , linesRejected
                    , noLinesRejected
                    , uploadedFile
                    , compositeIndexResolver)));
            // check all work is done and memory is in sync
            for (Future<?> futureBatch : futureBatches) {
                futureBatch.get(1, TimeUnit.HOURS);
            }
            lineIterator.close();
            uploadedFile.setNoData(linesImported == 0);
            uploadedFile.setProcessingDuration((System.currentTimeMillis() - track) / 1000);
            uploadedFile.setNoLinesImported(linesImported - noLinesRejected.get());
            connection.addToUserLogNoException("Imported " + (linesImported - noLinesRejected.get()) + " lines", true); // does the user log require more details??
            if (!linesRejected.isEmpty()) {
                // I'm going to have to go through the file and fine the rejected lines if they're there, can't really use the iterator before I don't think, we want the line raw from the BufferedReader as opposed to the CSV reader
                // I guess watch for possible performance issues . . .
                // this try should gracefully release the resources
                long time = System.currentTimeMillis();
                try (BufferedReader br = Files.newBufferedReader(Paths.get(uploadedFile.getPath()), uploadedFile.getParameter(DSImportService.FILEENCODING) != null ? Charset.forName(uploadedFile.getParameter(DSImportService.FILEENCODING)) : StandardCharsets.UTF_8)) {
                    lineNo = 0;
                    String line;
                    // I assume the line no as given by the json iterator starts at line 1, might have to check this . . .
                    while ((line = br.readLine()) != null) {
                        lineNo++;
                        if (linesRejected.containsKey(lineNo)) {
                            List<String> errors = linesRejected.remove(lineNo);
                            StringBuilder sb = new StringBuilder();
                            for (String error : errors) {
                                if (sb.length() > 0) {
                                    sb.append(", ");
                                }
                                sb.append(error);
                            }
                            rejectedLinesForUserFeedback.add(new UploadedFile.RejectedLine(lineNo, line, sb.toString()));
                            if (linesRejected.isEmpty()) { // we've grabbed all the rejected lines (removed above  . . .)
                                break;
                            }
                        }
                    }
                }
                System.out.println("millis to scan for error lines : " + (System.currentTimeMillis() - time));
                uploadedFile.setNoLinesRejected(noLinesRejected.get());
                uploadedFile.addToLinesRejected(rejectedLinesForUserFeedback);
            }
            // Delete check for tomcat temp files, if read from the other temp directly then leave it alone
            if (uploadedFile.getPath().contains("/usr/")) {
                File test = new File(uploadedFile.getPath());
                if (test.exists()) {
                    if (!test.delete()) {
                        System.out.println("unable to delete " + uploadedFile.getPath());
                    }
                }
            }
            System.out.println("---------- names found cache size " + namesFoundCache.size());
        } catch (Exception e) {
            System.out.println("Error on line " + lineNo);
            // the point of this is to add the file name to the exception message - I wonder if I should just leave a vanilla exception here and deal with this client side?
            e.printStackTrace();
            Throwable t = e;
            if (t.getCause() != null) { // once should do it, unwrap to reduce java.lang.exception being shown to the user
                t = t.getCause();
            }
            uploadedFile.setError(t.getMessage());
            //throw new Exception(fileName + " : " + t.getMessage());
        }
    }

    private static String checkNumeric(String lineValue) {
        //routine to catch data sent out in accountancy form - beware of data printed with ',' instead of '.'
        lineValue = lineValue.trim();
        if (lineValue.startsWith("0")) return lineValue;//don't bother with zip codes or 0.....
        if (lineValue.startsWith("(") && lineValue.endsWith(")")) {
            String middle = lineValue.substring(1, lineValue.length() - 1);
            try {
                double d = Double.parseDouble(middle.replace(",", ""));
                lineValue = -d + "";
                if (lineValue.endsWith(".0")) {
                    lineValue = lineValue.substring(0, lineValue.length() - 2);
                }
            } catch (Exception ignored) {

            }
        } else {
            if (lineValue.toLowerCase().endsWith("f")) return lineValue;
            if (lineValue.toLowerCase().endsWith("d")) return lineValue; // double can trip it too
            try {
                double d = Double.parseDouble(lineValue.replace(",", ""));
                if (!(d + "").contains("E")) { // this is hacky but big decimal to plain string got junk on fractions
                    lineValue = d + "";
                    if (lineValue.endsWith(".0")) {
                        lineValue = lineValue.substring(0, lineValue.length() - 2);
                    }
                }
            } catch (Exception ignored) {

            }
        }
        return lineValue;
    }
}