package com.azquo.admin;

/*
Created 12/09/2018

Report server logic for creating and restoring backups

 */

import com.azquo.admin.database.Database;
import com.azquo.admin.database.DatabaseDAO;
import com.azquo.admin.database.DatabaseServerDAO;
import com.azquo.admin.onlinereport.OnlineReport;
import com.azquo.admin.onlinereport.OnlineReportDAO;
import com.azquo.dataimport.ImportService;
import com.azquo.memorydb.DatabaseAccessToken;
import com.azquo.memorydb.NameForBackup;
import com.azquo.memorydb.ProvenanceForBackup;
import com.azquo.memorydb.ValueForBackup;
import com.azquo.rmi.RMIClient;
import com.azquo.spreadsheet.LoggedInUser;
import com.azquo.spreadsheet.SpreadsheetService;
import com.csvreader.CsvWriter;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvParser;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import org.apache.commons.lang.math.NumberUtils;
import org.zeroturnaround.zip.ZipUtil;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class BackupService {

    public static File createDBandReportsBackup(LoggedInUser loggedInUser) throws Exception {
        // ok, new code to dump a database and all reports. The former being the more difficult bit.
        File temp = File.createTempFile(loggedInUser.getDatabase().getName(), ".db");
        String tempPath = temp.getPath();
        createDBBackupFile(loggedInUser.getDatabase().getName(), loggedInUser.getDataAccessToken(), tempPath, loggedInUser.getDatabaseServer().getIp());
        temp.deleteOnExit();
        // so we've got the DB backup - now gather the reports and zip 'em up
        List<OnlineReport> onlineReports = OnlineReportDAO.findForDatabaseId(loggedInUser.getDatabase().getId());
        File[] filesToPack = new File[onlineReports.size() + 1];// +1 as it's the reports + the db . . .
        filesToPack[0] = temp;
        int index = 1;
        for (OnlineReport onlineReport : onlineReports) {
            // todo - can we chop the id prefix?
            filesToPack[index] = new File(SpreadsheetService.getHomeDir() + ImportService.dbPath + loggedInUser.getBusinessDirectory() + ImportService.onlineReportsDir + onlineReport.getFilenameForDisk());
            index++;
        }
        File tempzip = File.createTempFile(loggedInUser.getDatabase().getName(), ".zip");
        System.out.println("temp zip " + tempzip.getPath());
        ZipUtil.packEntries(filesToPack, tempzip);
        tempzip.deleteOnExit();
        return tempzip;
    }

    public static String loadBackup(LoggedInUser loggedInUser, File file, String database) throws Exception {
        StringBuilder toReturn = new StringBuilder();
        System.out.println("attempting backup restore on " + file.getPath());
        ZipUtil.explode(file);
        // after exploding the original file is replaced with a directory
        File zipDir = new File(file.getPath());
        File[] files = zipDir.listFiles();
        // need to load the database first
        for (File f : files) {
            if (f.getName().endsWith(".db")) {
                loadDBBackup(loggedInUser, f, database, toReturn, false);
            }
        }
        // now reports
        for (File f : files) {
            if (!f.getName().endsWith(".db")) {
                // rename the xlsx file to get rid of the ID that will probably be in front in the backup zip
                if (f.getName().contains("-") && NumberUtils.isNumber(f.getName().substring(0, f.getName().indexOf("-")))){
                    toReturn.append(ImportService.importTheFile(loggedInUser, f.getName().substring(f.getName().indexOf("-") + 1), f.getAbsolutePath(), false).replace("\n", "<br/>") + "<br/>");
                } else {
                    toReturn.append(ImportService.importTheFile(loggedInUser, f.getName(), f.getAbsolutePath(), false).replace("\n", "<br/>") + "<br/>");
                }
            }
        }
        return toReturn.toString();
    }

    // should be in shared?
    static int batchSize = 100_000;

    public static void loadDBBackup(LoggedInUser loggedInUser, File file, String database, StringBuilder log, boolean justEmpty) {
        int line = 1;
        try {
            CsvMapper csvMapper = new CsvMapper();
            csvMapper.enable(CsvParser.Feature.WRAP_AS_ARRAY);
            CsvSchema schema = csvMapper.schemaFor(String[].class)
                    .withColumnSeparator('\t')
                    .withLineSeparator("\n")
                    .withoutQuoteChar();
            MappingIterator<String[]> lineIterator = csvMapper.reader(String[].class).with(schema).readValues(
                    new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8));
            String[] lineValues = lineIterator.next();
            line++;
            if (database == null || database.trim().isEmpty()){
                database = lineValues[0];
            }
            // first thing to do is delete the database and all associated reports
            Database db = DatabaseDAO.findForNameAndBusinessId(database, loggedInUser.getUser().getBusinessId());
            if (db != null) {
                // hack in a security check, we know the user is an Admin or Developer but if a developer it needs to be their DB
                if (loggedInUser.getUser().isDeveloper() && loggedInUser.getUser().getId() != db.getUserId()){
                    throw new Exception("A developer cannot restore to a database that is not thiers");
                }
                if (justEmpty){
                    loggedInUser.setDatabaseWithServer(DatabaseServerDAO.findById(db.getDatabaseServerId()), db);
                    AdminService.emptyDatabase(loggedInUser, false); // don't load the setup file!
                } else {
                    AdminService.removeDatabaseByIdWithBasicSecurity(loggedInUser, db.getId());
                    AdminService.createDatabase(db.getName(), db.getDatabaseType(), loggedInUser, DatabaseServerDAO.findById(db.getDatabaseServerId()));
                }
            } else {
                // still not really dealing with different servers properly
                AdminService.createDatabase(database, "", loggedInUser, loggedInUser.getDatabaseServer());
            }
            // refresh the db, it probabl;y will have a different ID
            List<NameForBackup> namesForBackup = new ArrayList<>();
            List<ValueForBackup> valuesForBackup = new ArrayList<>();
            List<ValueForBackup> valueHistoriesForBackup = new ArrayList<>();
            List<ProvenanceForBackup> provenanceForBackups = new ArrayList<>();
            // revised logic, we start with provenance
            while (lineIterator.hasNext()) {
                lineValues = lineIterator.next();
                line++;
                if ("NAMES".equals(lineValues[0])) {
                    RMIClient.getServerInterface(loggedInUser.getDatabaseServer().getIp()).sendBatchOfProvenanceFromBackup(loggedInUser.getDataAccessToken(), provenanceForBackups);
                    break;
                }
                ProvenanceForBackup provenanceForBackup = new ProvenanceForBackup(
                        Integer.parseInt(lineValues[0])
                        , lineValues[1]
                );
                provenanceForBackups.add(provenanceForBackup);
                if (provenanceForBackups.size() == batchSize) {
                    // send to server
                    RMIClient.getServerInterface(loggedInUser.getDatabaseServer().getIp()).sendBatchOfProvenanceFromBackup(loggedInUser.getDataAccessToken(), provenanceForBackups);
                    provenanceForBackups = new ArrayList<>();
                }
            }

            while (lineIterator.hasNext()) { // the main line reading loop
                lineValues = lineIterator.next();
                line++;
                if ("VALUES".equals(lineValues[0])) {
                    // put in the left over names then link them
                    RMIClient.getServerInterface(loggedInUser.getDatabaseServer().getIp()).sendBatchOfNamesFromBackup(loggedInUser.getDataAccessToken(), namesForBackup);
                    break;
                }
                byte[] decodedBytes = Base64.getDecoder().decode(lineValues[3]);
                NameForBackup nameForBackup = new NameForBackup(
                        Integer.parseInt(lineValues[0])
                        , Integer.parseInt(lineValues[1])
                        , lineValues[2].replace("\\\\n", "\n").replace("\\\\t", "\t")
                        , decodedBytes
                        , Integer.parseInt(lineValues[4])
                        , Integer.parseInt(lineValues[5])
                );
                namesForBackup.add(nameForBackup);
                if (namesForBackup.size() == batchSize) {
                    RMIClient.getServerInterface(loggedInUser.getDatabaseServer().getIp()).sendBatchOfNamesFromBackup(loggedInUser.getDataAccessToken(), namesForBackup);
                    // send to server
                    namesForBackup = new ArrayList<>();
                }
            }
            // now values
            while (lineIterator.hasNext()) { // the main line reading loop
                lineValues = lineIterator.next();
                line++;
                if ("VALUEHISTORY".equals(lineValues[0])) {
                    RMIClient.getServerInterface(loggedInUser.getDatabaseServer().getIp()).sendBatchOfValuesFromBackup(loggedInUser.getDataAccessToken(), valuesForBackup);
                    break;
                }
                byte[] decodedBytes = Base64.getDecoder().decode(lineValues[3]);
                ValueForBackup valueForBackup = new ValueForBackup(
                        Integer.parseInt(lineValues[0])
                        , Integer.parseInt(lineValues[1])
                        , lineValues[2]
                        , decodedBytes
                );
                valuesForBackup.add(valueForBackup);
                if (valuesForBackup.size() == batchSize) {
                    RMIClient.getServerInterface(loggedInUser.getDatabaseServer().getIp()).sendBatchOfValuesFromBackup(loggedInUser.getDataAccessToken(), valuesForBackup);
                    // send to server
                    valuesForBackup = new ArrayList<>();
                }
            }
            // now values history
            while (lineIterator.hasNext()) { // the main line reading loop
                lineValues = lineIterator.next();
                line++;
                byte[] decodedBytes = Base64.getDecoder().decode(lineValues[3]);
                ValueForBackup valueForBackup = new ValueForBackup(
                        Integer.parseInt(lineValues[0])
                        , Integer.parseInt(lineValues[1])
                        , lineValues[2]
                        , decodedBytes
                );
                valueHistoriesForBackup.add(valueForBackup);
                if (valueHistoriesForBackup.size() == batchSize) {
                    RMIClient.getServerInterface(loggedInUser.getDatabaseServer().getIp()).sendBatchOfValueHistoriesFromBackup(loggedInUser.getDataAccessToken(), valueHistoriesForBackup);
                    // send to server
                    valueHistoriesForBackup = new ArrayList<>();
                }
            }
            // and any left over at the end
            RMIClient.getServerInterface(loggedInUser.getDatabaseServer().getIp()).sendBatchOfValueHistoriesFromBackup(loggedInUser.getDataAccessToken(), valueHistoriesForBackup);
            // and link which will also persist after . . .
            RMIClient.getServerInterface(loggedInUser.getDatabaseServer().getIp()).linkNamesForBackupRestore(loggedInUser.getDataAccessToken());
            AdminService.updateNameAndValueCounts(loggedInUser, DatabaseDAO.findForNameAndBusinessId(database, loggedInUser.getUser().getBusinessId()));
        } catch (Exception e) {
            log.append("Error : " + e.getMessage() + " on line " + line);
            e.printStackTrace();
        }
    }

    public static void createDBBackupFile(String databaseName, DatabaseAccessToken databaseAccessToken, String filePath, String databaseServerIP) throws Exception {
        System.out.println("attempting to create backup file " + filePath);
        FileOutputStream fos = new FileOutputStream(filePath);
        CsvWriter csvW = new CsvWriter(fos, '\t', Charset.forName("UTF-8"));
        csvW.setUseTextQualifier(false);
        csvW.write(databaseName);
        csvW.endRecord();
// we start with provenance now
        int batchNumber = 0;
        List<ProvenanceForBackup> provenancesForBackup =
                RMIClient.getServerInterface(databaseServerIP).getBatchOfProvenanceForBackup(databaseAccessToken, batchNumber);
        while (!provenancesForBackup.isEmpty()) {
            for (ProvenanceForBackup provenanceForBackup : provenancesForBackup) {
                csvW.write(provenanceForBackup.getId() + "");
                csvW.write(provenanceForBackup.getJson() + "");
                csvW.endRecord();
            }
            batchNumber++;
            provenancesForBackup =
                    RMIClient.getServerInterface(databaseServerIP).getBatchOfProvenanceForBackup(databaseAccessToken, batchNumber);
        }
        csvW.write("NAMES");
        csvW.endRecord();
        batchNumber = 0;
        List<NameForBackup> namesForBackup =
                RMIClient.getServerInterface(databaseServerIP).getBatchOfNamesForBackup(databaseAccessToken, batchNumber);
        while (!namesForBackup.isEmpty()) {
            for (NameForBackup nameForBackup : namesForBackup) {
                csvW.write(nameForBackup.getId() + "");
                csvW.write(nameForBackup.getProvenanceId() + "");
                // attributes could have unhelpful chars
                csvW.write(nameForBackup.getAttributes().replace("\n", "\\\\n").replace("\r", "").replace("\t", "\\\\t"));
                //base 64 encode the bytes
                byte[] encodedBytes = Base64.getEncoder().encode(nameForBackup.getChildren());
                String string64 = new String(encodedBytes);
                csvW.write(string64);
                csvW.write(nameForBackup.getNoParents() + "");
                csvW.write(nameForBackup.getNoValues() + "");
                csvW.endRecord();
            }
            batchNumber++;
            namesForBackup = RMIClient.getServerInterface(databaseServerIP).getBatchOfNamesForBackup(databaseAccessToken, batchNumber);
        }
        csvW.write("VALUES");
        csvW.endRecord();
        batchNumber = 0;
        List<ValueForBackup> valuessForBackup =
                RMIClient.getServerInterface(databaseServerIP).getBatchOfValuesForBackup(databaseAccessToken, batchNumber);
        while (!valuessForBackup.isEmpty()) {
            for (ValueForBackup valueForBackup : valuessForBackup) {
                csvW.write(valueForBackup.getId() + "");
                csvW.write(valueForBackup.getProvenanceId() + "");
                // attributes could have unhelpful chars
                csvW.write(valueForBackup.getText());
                //base 64 encode the bytes
                byte[] encodedBytes = Base64.getEncoder().encode(valueForBackup.getNames());
                String string64 = new String(encodedBytes);
                csvW.write(string64);
                csvW.endRecord();
            }
            batchNumber++;
            valuessForBackup =
                    RMIClient.getServerInterface(databaseServerIP).getBatchOfValuesForBackup(databaseAccessToken, batchNumber);
        }

        csvW.write("VALUEHISTORY");
        csvW.endRecord();
        batchNumber = 0;
        valuessForBackup =
                RMIClient.getServerInterface(databaseServerIP).getBatchOfValuesHistoryForBackup(databaseAccessToken, batchNumber);
        while (!valuessForBackup.isEmpty()) {
            for (ValueForBackup valueForBackup : valuessForBackup) {
                csvW.write(valueForBackup.getId() + "");
                csvW.write(valueForBackup.getProvenanceId() + "");
                // attributes could have unhelpful chars
                csvW.write(valueForBackup.getText());
                //base 64 encode the bytes
                byte[] encodedBytes = Base64.getEncoder().encode(valueForBackup.getNames());
                String string64 = new String(encodedBytes);
                csvW.write(string64);
                csvW.endRecord();
            }
            batchNumber++;
            valuessForBackup =
                    RMIClient.getServerInterface(databaseServerIP).getBatchOfValuesHistoryForBackup(databaseAccessToken, batchNumber);
        }
        csvW.close();
        fos.close();

    }
}
