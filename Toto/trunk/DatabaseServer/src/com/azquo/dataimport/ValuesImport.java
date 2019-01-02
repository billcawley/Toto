package com.azquo.dataimport;

import com.azquo.ThreadPools;
import com.azquo.TypedPair;
import com.azquo.memorydb.AzquoMemoryDBConnection;
import com.azquo.memorydb.core.Name;
import com.azquo.spreadsheet.transport.UploadedFile;
import com.fasterxml.jackson.databind.MappingIterator;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/*
This class batches up data to be loaded doing simple checks on cell values
The actual loading and complex heading resolution should be done in other classes,

The cell on a line can be a value or an attribute or a name - or a part of another cell via composite. Or, now, an attribute name.

 */
public class ValuesImport {

    // where we store import specs for different file types
    public static final String ALLIMPORTSHEETS = "All import sheets";

    static void valuesImport(AzquoMemoryDBConnection connection, MappingIterator<String[]> lineIterator
            , UploadedFile uploadedFile, List<ImmutableImportHeading> importHeadings
            , int batchSize, int lastColumnToActuallyRead, CompositeIndexResolver compositeIndexResolver) {
        try {
            long track = System.currentTimeMillis();
            // now, since this will be multi threaded need to make line objects to batch up. Cannot be completely immutable due to the current logic e.g. composite values
            ArrayList<TypedPair<Integer, List<ImportCellWithHeading>>> linesBatched = new ArrayList<>(batchSize);
            List<Future> futureBatches = new ArrayList<>();
            // Local cache of names just to speed things up, a name could be referenced millions of times in one file
            final Map<String, Name> namesFoundCache = new ConcurrentHashMap<>();
            final Set<String> linesRejected = Collections.newSetFromMap(new ConcurrentHashMap<>(0)); // track line numbers rejected
            int linesImported = 0; // just for some feedback at the end
            int importLine = 0; // for error checking etc. Generally the first line of data is line 2, this is incremented at the beginning of the loop. Might be inaccurate if there are vertically stacked headings.
            while (lineIterator.hasNext()) { // the main line reading loop
                String[] lineValues = lineIterator.next();
                importLine++;
                linesImported++;
                List<ImportCellWithHeading> importCellsWithHeading = new ArrayList<>();
                int columnIndex = 0;
                boolean corrupt = false;
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
                    importCellsWithHeading.add(new ImportCellWithHeading(immutableImportHeading, lineValue));
                    columnIndex++;
                }
                if (!corrupt && !blankLine) {
                    linesBatched.add(new TypedPair<>(importLine,importCellsWithHeading));
                    // Start processing this batch. As the file is read the active threads will rack up to the maximum number allowed rather than starting at max. Store the futures to confirm all are done after all lines are read.
                    // batch size is derived by getLineIteratorAndBatchSize
                    if (linesBatched.size() == batchSize) {
                        futureBatches.add(ThreadPools.getMainThreadPool().submit(

                                new BatchImporter(connection
                                        , uploadedFile.getNoValuesAdjusted(), linesBatched
                                        , namesFoundCache, uploadedFile.getLanguages()
                                        , linesRejected, uploadedFile.getParameter("cleardata") != null, compositeIndexResolver))// line no should be the start

                        );
                        linesBatched = new ArrayList<>(batchSize);
                    }
                } else {
                    linesImported--; // drop it down as we're not even going to try that line
                }
            }
            futureBatches.add(ThreadPools.getMainThreadPool().submit(new BatchImporter(connection
                    , uploadedFile.getNoValuesAdjusted(), linesBatched, namesFoundCache, uploadedFile.getLanguages(), linesRejected, uploadedFile.getParameter("cleardata") != null, compositeIndexResolver)));
            // check all work is done and memory is in sync
            for (Future<?> futureBatch : futureBatches) {
                futureBatch.get(1, TimeUnit.HOURS);
            }
            lineIterator.close();
            // Delete check for tomcat temp files, if read from the other temp directly then leave it alone
            if (uploadedFile.getPath().contains("/usr/")) {
                File test = new File(uploadedFile.getPath());
                if (test.exists()) {
                    if (!test.delete()) {
                        System.out.println("unable to delete " + uploadedFile.getPath());
                    }
                }
            }
            uploadedFile.setProcessingDuration((System.currentTimeMillis() - track) / 1000);
            // hacky - make a better solution - todo
            Set<String> linesRejectedTest = new HashSet<>();
            for (String rejected : linesRejected){
                linesRejectedTest.add(rejected.substring(0, rejected.indexOf(":")));
            }
            uploadedFile.setNoLinesImported(linesImported - linesRejectedTest.size());

            // add a bit of feedback for rejected lines. Factor? It's not complex stuff.
            if (!linesRejected.isEmpty()) {
                ArrayList<String> lineNumbersList = new ArrayList<>(linesRejected);
                lineNumbersList.sort((s, t1) -> {
                    try {
                        if (s.contains(":") && t1.contains(":")) {
                            return Integer.compare(Integer.parseInt(s.substring(0, s.indexOf(":")).trim()), Integer.parseInt(t1.substring(0, t1.indexOf(":")).trim()));
                        }
                    } catch (Exception ignored) {
                    }
                    return 0;
                }); // should do the basic sort
                uploadedFile.addToLinesRejected(lineNumbersList);
            }
            connection.addToUserLogNoException("Imported " + (linesImported - linesRejected.size()) + " lines", true); // does the user log require more details??
            System.out.println("---------- names found cache size " + namesFoundCache.size());
        } catch (Exception e) {
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
            try {

                double d = Double.parseDouble(lineValue.replace(",", ""));
                lineValue = d + "";
                if (lineValue.endsWith(".0")) {
                    lineValue = lineValue.substring(0, lineValue.length() - 2);
                }
            } catch (Exception ignored) {

            }
        }
        return lineValue;
    }
}