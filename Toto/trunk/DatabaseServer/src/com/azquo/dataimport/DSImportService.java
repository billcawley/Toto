package com.azquo.dataimport;

import com.azquo.StringLiterals;
import com.azquo.memorydb.AzquoMemoryDBConnection;
import com.azquo.memorydb.DatabaseAccessToken;
import com.azquo.spreadsheet.transport.UploadedFile;

import java.util.Map;
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

    public static UploadedFile readPreparedFile(final DatabaseAccessToken databaseAccessToken, UploadedFile uploadedFile, final String user) throws Exception {
        System.out.println("Reading file " + uploadedFile.getPath());
        AzquoMemoryDBConnection azquoMemoryDBConnection = AzquoMemoryDBConnection.getConnectionFromAccessToken(databaseAccessToken);
        azquoMemoryDBConnection.setProvenance(user, "imported", uploadedFile.getFileNamessAsString(), "");
        // if the provenance is unused I could perhaps zap it but it's not a big deal for the mo
        UploadedFile toReturn = readPreparedFile(azquoMemoryDBConnection, uploadedFile, new AtomicInteger());
        toReturn.setDataModified(!azquoMemoryDBConnection.isUnusedProvenance());
        return toReturn;
    }

    // Called by above but also directly from SSpreadsheet service when it has prepared a CSV from data entered ad-hoc into a sheet
    // I wonder if the valuesModifiedCounter is a bit hacky, will maybe revisit this later
    // EFC - parameters going up, should a configuration/context object be passed?
    public static UploadedFile readPreparedFile(AzquoMemoryDBConnection azquoMemoryDBConnection, UploadedFile uploadedFile, AtomicInteger valuesModifiedCounter) throws Exception {
        // ok the thing he is to check if the memory db object lock is free, more specifically don't start an import if persisting is going on, since persisting never calls import there should be no chance of a deadlock from this
        // of course this doesn't currently stop the opposite, a persist being started while an import is going on.
        azquoMemoryDBConnection.lockTest();
        azquoMemoryDBConnection.getAzquoMemoryDB().clearCaches();
        if (uploadedFile.getFileName().toLowerCase().startsWith("sets")) { // typically from a sheet with that name in a book
            // not currently paying attention to isSpreadsheet - only possible issue is the replacing of \\\n with \n required based off writeCell in ImportFileUtilities
            SetsImport.setsImport(azquoMemoryDBConnection, uploadedFile);
        } else {
            boolean clearData = uploadedFile.getFileName().toLowerCase().contains("cleardata");
            ValuesImportConfig valuesImportConfig = new ValuesImportConfig(azquoMemoryDBConnection, uploadedFile, valuesModifiedCounter, clearData);
            // a lot goes on in this function to do with checking the file, finding import configuration, resolving headings etc.
            ValuesImportConfigProcessor.prepareValuesImportConfig(valuesImportConfig);
            // when it is done we assume we're ready to batch up lines with headers and import with BatchImporter
            ValuesImport.valuesImport(valuesImportConfig);
            // now look to see if there's a need to execute after import
            // find interpreter being called again - a way not to do this?
            if (valuesImportConfig.getImportInterpreter() != null) {
                String execute = valuesImportConfig.getImportInterpreter().getAttribute("EXECUTE");
                if (execute != null && execute.length() > 0) {
                    uploadedFile.setExecute(execute);
                }
            }
        }
        return uploadedFile; // it will (should!) have been modified
    }
}