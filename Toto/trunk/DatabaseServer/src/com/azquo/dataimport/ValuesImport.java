package com.azquo.dataimport;

import com.azquo.ThreadPools;
import com.azquo.memorydb.core.Name;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/*
Edd Aug 2018
THe importing is becoming quite complex and opaque. I'm hoping that breaking things up will help to bring it under control.

This class batches up data to be loaded doing simple checks on cell values
The actual loading and complex header resolution should be done in other classes,

The cell on a line can be a value or an attribute or a name - or a part of another cell via composite. Or, now, an attribute name.

 */
public class ValuesImport {

    // where we store import specs for different file types
    public static final String ALLIMPORTSHEETS = "All import sheets";

    static void valuesImport(ValuesImportConfig valuesImportConfig) {
        try {
            long track = System.currentTimeMillis();
            // now, since this will be multi threaded need to make line objects to batch up. Cannot be completely immutable due to the current logic e.g. composite values
            ArrayList<List<ImportCellWithHeading>> linesBatched = new ArrayList<>(valuesImportConfig.getBatchSize());
            List<Future> futureBatches = new ArrayList<>();
            // Local cache of names just to speed things up, a name could be referenced millions of times in one file
            final Map<String, Name> namesFoundCache = new ConcurrentHashMap<>();
            final Set<String> linesRejected = Collections.newSetFromMap(new ConcurrentHashMap<>(0)); // track line numbers rejected
            int linesImported = 0; // just for some feedback at the end
            int importLine = 0; // for error checking etc. Generally the first line of data is line 2, this is incremented at the beginning of the loop. Might be inaccurate if there are vertically stacked headers.
            while (valuesImportConfig.getLineIterator().hasNext()) { // the main line reading loop
                String[] lineValues = valuesImportConfig.getLineIterator().next();
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
                for (ImmutableImportHeading immutableImportHeading : valuesImportConfig.getHeadings()) {
                      // Intern may save a little memory if strings are repeated a lot. Column Index could point past line values for things like composite.
                    String lineValue = (cellCount++< valuesImportConfig.getLineCells() && columnIndex < lineValues.length) ? lineValues[columnIndex].trim().intern() : "";
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
                    if (valuesImportConfig.getUploadedFile().isConvertedFromWorksheet()) {
                        lineValue = lineValue.replace("\\\\t", "\t").replace("\\\\n", "\n");
                    }
                    lineValue = checkNumeric(lineValue);
                    importCellsWithHeading.add(new ImportCellWithHeading(immutableImportHeading, lineValue));
                    columnIndex++;
                }
                if (!corrupt && !blankLine) {
                    linesBatched.add(importCellsWithHeading);
                    // Start processing this batch. As the file is read the active threads will rack up to the maximum number allowed rather than starting at max. Store the futures to confirm all are done after all lines are read.
                    // batch size is derived by getLineIteratorAndBatchSize
                    if (linesBatched.size() == valuesImportConfig.getBatchSize()) {
                        futureBatches.add(ThreadPools.getMainThreadPool().submit(

                                new BatchImporter(valuesImportConfig.getAzquoMemoryDBConnection()
                                        , valuesImportConfig.getValuesModifiedCounter(), linesBatched
                                        , namesFoundCache, valuesImportConfig.getLanguages()
                                        , importLine - valuesImportConfig.getBatchSize(), linesRejected, valuesImportConfig.getUploadedFile().getParameters().get("cleardata") != null))// line no should be the start

                        );
                        linesBatched = new ArrayList<>(valuesImportConfig.getBatchSize());
                    }
                } else {
                    linesImported--; // drop it down as we're not even going to try that line
                }
            }
            // load leftovers
            int loadLine = importLine - linesBatched.size(); // NOT batch size! A problem here isn't a functional problem but it makes logging incorrect.
            futureBatches.add(ThreadPools.getMainThreadPool().submit(new BatchImporter(valuesImportConfig.getAzquoMemoryDBConnection()
                    , valuesImportConfig.getValuesModifiedCounter(), linesBatched, namesFoundCache, valuesImportConfig.getLanguages(), loadLine, linesRejected, valuesImportConfig.getUploadedFile().getParameters().get("cleardata") != null)));
            // check all work is done and memory is in sync
            for (Future<?> futureBatch : futureBatches) {
                futureBatch.get(1, TimeUnit.HOURS);
            }
            // wasn't closing before, maybe why the files stayed there on Windows. Use the original one to close in case the lineIterator field was reassigned by transpose
            valuesImportConfig.getOriginalIterator().close();
            // Delete check for tomcat temp files, if read from the other temp directly then leave it alone
            if (valuesImportConfig.getUploadedFile().getPath().contains("/usr/")) {
                File test = new File(valuesImportConfig.getUploadedFile().getPath());
                if (test.exists()) {
                    if (!test.delete()) {
                        System.out.println("unable to delete " + valuesImportConfig.getUploadedFile().getPath());
                    }
                }
            }
            valuesImportConfig.getUploadedFile().setProcessingDuration((System.currentTimeMillis() - track) / 1000);
            valuesImportConfig.getUploadedFile().setNoLinesImported(linesImported - linesRejected.size());
            valuesImportConfig.getUploadedFile().setNoValuesAdjusted(valuesImportConfig.getValuesModifiedCounter().get());

            // add a bit of feedback for rejected lines. Factor? It's not complex stuff.
            if (!linesRejected.isEmpty()) {
                ArrayList<String> lineNumbersList = new ArrayList<>(linesRejected);
                Collections.sort(lineNumbersList); // should do the basic sort
                valuesImportConfig.getUploadedFile().addToLinesRejected(lineNumbersList);
            }
            valuesImportConfig.getAzquoMemoryDBConnection().addToUserLogNoException("Imported " + (linesImported - linesRejected.size()) + " lines", true); // does the user log require more details??
            System.out.println("---------- names found cache size " + namesFoundCache.size());
        } catch (Exception e) {
            // the point of this is to add the file name to the exception message - I wonder if I should just leave a vanilla exception here and deal with this client side?
            e.printStackTrace();
            Throwable t = e;
            if (t.getCause() != null) { // once should do it, unwrap to reduce java.lang.exception being shown to the user
                t = t.getCause();
            }
            valuesImportConfig.getUploadedFile().setError(t.getMessage());
            //throw new Exception(fileName + " : " + t.getMessage());
        }
    }

    private static String checkNumeric(String lineValue){
        //routine to catch data sent out in accountancy form - beware of data printed with ',' instead of '.'
        lineValue = lineValue.trim();
        if (lineValue.startsWith("0")) return lineValue;//don't bother with zip codes or 0.....
        if (lineValue.startsWith("(") && lineValue.endsWith(")")){
            String middle = lineValue.substring(1,lineValue.length() - 1);
            try {
                double d = Double.parseDouble(middle.replace(",",""));
                lineValue = -d + "";
                if (lineValue.endsWith(".0")){
                    lineValue = lineValue.substring(0,lineValue.length() - 2);
                }
            }catch(Exception ignored){

            }
        }else{
            if (lineValue.toLowerCase().endsWith("f")) return lineValue;
            try{

                double d = Double.parseDouble(lineValue.replace(",",""));
                lineValue = d + "";
                if (lineValue.endsWith(".0")){
                    lineValue = lineValue.substring(0,lineValue.length() - 2);
                }
            }catch(Exception ignored){

            }
        }
        return lineValue;
    }
}