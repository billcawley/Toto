package com.azquo.dataimport;

import com.azquo.memorydb.AzquoMemoryDBConnection;
import com.azquo.memorydb.DatabaseAccessToken;

import java.util.concurrent.atomic.AtomicInteger;

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

    /*
    Currently only two types of import supported and detection on file name (best idea?). Run the import and persist.
    Generally speaking creating the import headers and basic set structure is what is required to ready a database to load data.

    EFC - Zip name is as it says, the name of the zip file (excluding the extension) if the file was originally part of a zip file.
    Whether this should be used is another matter.

    */
    public static String readPreparedFile(DatabaseAccessToken databaseAccessToken, String filePath, String fileName
            , String zipName, String user, boolean persistAfter, boolean isSpreadsheet) throws Exception {
        System.out.println("Reading file " + filePath);
        AzquoMemoryDBConnection azquoMemoryDBConnection = AzquoMemoryDBConnection.getConnectionFromAccessToken(databaseAccessToken);
        // in an ad hoc spreadsheet area should it say imported? Hard to detect at this point. isSpreadsheet means it could be an Excel file import, a different thing from a data entry area.
        azquoMemoryDBConnection.setProvenance(user, fileName.contains("duplicates") ? "imported with duplicates" : "imported", fileName, "");
        if (fileName.contains(":")) {
            fileName = fileName.substring(fileName.indexOf(":") + 1);//remove the workbook name.  sent only for the provenance.
        }
        return readPreparedFile(azquoMemoryDBConnection, filePath, fileName, zipName, persistAfter, isSpreadsheet, new AtomicInteger());
    }

    // Called by above but also directly from DSSpreadsheet service when it has prepared a CSV from data entered ad-hoc into a sheet
    // I wonder if the valuesModifiedCounter is a bit hacky, will maybe revisit this later
    // EFC - parameters going up, should a configuration object be passed?
    public static String readPreparedFile(AzquoMemoryDBConnection azquoMemoryDBConnection, String filePath, String fileName
            , String zipName, boolean persistAfter, boolean isSpreadsheet, AtomicInteger valuesModifiedCounter) throws Exception {
        // ok the thing he is to check if the memory db object lock is free, more specifically don't start an import if persisting is going on, since persisting never calls import there should be no chance of a deadlock from this
        // of course this doesn't currently stop the opposite, a persist being started while an import is going on.
        azquoMemoryDBConnection.lockTest();
        azquoMemoryDBConnection.getAzquoMemoryDB().clearCaches();
        String toReturn;
        if (fileName.toLowerCase().startsWith("sets")) { // typically from a sheet with that name in a book
            // not currently paying attention to isSpreadsheet - only possible issue is the replacing of \\\n with \n required based off writeCell in ImportFileUtilities
            toReturn = SetsImport.setsImport(azquoMemoryDBConnection, filePath, fileName);
        } else {
            boolean clearData = fileName.toLowerCase().contains("cleardata");
            ValuesImportConfig valuesImportConfig = new ValuesImportConfig(azquoMemoryDBConnection, filePath, fileName, zipName, isSpreadsheet, valuesModifiedCounter, clearData);
            // a lot goes on in this function to do with checking the file, finding import configuration, resolving headings etc.
            ValuesImportConfigProcessor.prepareValuesImportConfig(valuesImportConfig);
            // when it is done we assume we're ready to batch up lines with headers and import with BatchImporter
            toReturn = ValuesImport.valuesImport(valuesImportConfig);
            // now look to see if there's a need to execute after import
            // find interpreter being called again - a way not to do this?
            if (valuesImportConfig.getImportInterpreter() != null) {
                String execute = valuesImportConfig.getImportInterpreter().getAttribute("EXECUTE");
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
}