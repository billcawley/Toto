package com.azquo.admin;

/*
Created 12/09/2018

Report server logic for creating and restoring backups

 */

import com.azquo.admin.controller.ManageDatabasesController;
import com.azquo.admin.database.*;
import com.azquo.admin.onlinereport.DatabaseReportLinkDAO;
import com.azquo.admin.onlinereport.OnlineReport;
import com.azquo.admin.onlinereport.OnlineReportDAO;
import com.azquo.admin.user.User;
import com.azquo.admin.user.UserDAO;
import com.azquo.dataimport.*;
import com.azquo.memorydb.DatabaseAccessToken;
import com.azquo.memorydb.NameForBackup;
import com.azquo.memorydb.ProvenanceForBackup;
import com.azquo.memorydb.ValueForBackup;
import com.azquo.rmi.RMIClient;
import com.azquo.spreadsheet.LoggedInUser;
import com.azquo.spreadsheet.SpreadsheetService;
import com.azquo.spreadsheet.transport.UploadedFile;
import com.csvreader.CsvWriter;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvParser;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.math.NumberUtils;
import org.zeroturnaround.zip.FileSource;
import org.zeroturnaround.zip.ZipEntrySource;
import org.zeroturnaround.zip.ZipUtil;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;

import static com.azquo.dataimport.ImportService.dbPath;
import static com.azquo.dataimport.ImportService.importTemplatesDir;


public class BackupService {
    private static final String CATEGORYBREAK = "~~~"; // I'm not proud of this
    private static final String CATEGORYBREAKOLD = "|||"; // tripped up windows
    private static final String TYPEBREAK = "~~T~~"; // I'm not proud of this

    public static File createDBandReportsAndTemplateBackup(LoggedInUser loggedInUser, String nameSubset, boolean justReports) throws Exception {
        // ok, new code to dump a database and all reports. The former being the more difficult bit.
        File dbFile = null;
        if (!justReports) {
            dbFile = createDBBackupFile(loggedInUser.getDatabase().getName(), nameSubset, loggedInUser.getDataAccessToken(), loggedInUser.getDatabaseServer());
        }
        // rather than guessing the size of the array just do this
        List<ZipEntrySource> toZip = new ArrayList<>();
        // so we've got the DB backup - now gather the reports and zip 'em up
        List<OnlineReport> onlineReports = OnlineReportDAO.findForDatabaseId(loggedInUser.getDatabase().getId());
        ImportTemplate importTemplate = loggedInUser.getDatabase().getImportTemplateId() != -1 ? ImportTemplateDAO.findById(loggedInUser.getDatabase().getImportTemplateId()) : null;
        // ok now need to check for uploads with file types
        List<UploadRecord> forDatabaseIdWithFileType = UploadRecordDAO.findForDatabaseIdWithFileType(loggedInUser.getDatabase().getId());
        Map<String, List<UploadRecord>> groupedByFileType = new HashMap<>();
        for (UploadRecord uploadRecord : forDatabaseIdWithFileType) {
            groupedByFileType.computeIfAbsent(uploadRecord.getFileType(), t -> new ArrayList<>()).add(uploadRecord);
        }
        int limit = 4;
        Set<String> zipFiles = new HashSet<>();
        for (String type : groupedByFileType.keySet()) {
            List<UploadRecord> forType = groupedByFileType.get(type);
            // check each file exists!
            forType.removeIf(uploadRecord -> !Files.exists(Paths.get(uploadRecord.getTempPath())));
            // date sort for dedupe and trimming
            forType.sort((uploadRecord, t1) -> {
                return -uploadRecord.getDate().compareTo(t1.getDate()); // - as we want descending
            });

            if (forType.size() > limit) {
                forType = forType.subList(0, limit);
            }
            // iterator can remove
            Iterator<UploadRecord> uploadRecordIterator = forType.iterator();
            String lastName = null;
            while (uploadRecordIterator.hasNext()) {
                UploadRecord check = uploadRecordIterator.next();
                if (check.getFileName().equalsIgnoreCase(lastName)) {
                    uploadRecordIterator.remove();
                } else {
                    lastName = check.getFileName();
                }
            }
            groupedByFileType.put(type, forType); // re set it as forType may have been reassigned by sublist
        }
        if (dbFile != null) {
            toZip.add(new FileSource(dbFile.getName(), dbFile));
            zipFiles.add(dbFile.getName());
        }
        for (OnlineReport onlineReport : onlineReports) {
            String category = null;
            if (onlineReport.getCategory() != null && !onlineReport.getCategory().isEmpty()) {
                category = onlineReport.getCategory();
            }
            // this little chunk to stop duplicate entries
            String zipPath = (category != null ? category + CATEGORYBREAK : "") + onlineReport.getFilename();
            if (zipFiles.contains(zipPath)) {
                int counter = 0;
                while (zipFiles.contains(counter + zipPath)) {
                    counter++;
                }
                zipPath = counter + zipPath;
            }

            toZip.add(new FileSource(zipPath, new File(SpreadsheetService.getHomeDir() + ImportService.dbPath + loggedInUser.getBusinessDirectory() + ImportService.onlineReportsDir + onlineReport.getFilenameForDisk())));
            zipFiles.add(zipPath);
        }
        if (importTemplate != null) {
            // this little chunk to stop duplicate entries
            String zipPath = importTemplate.getFilenameForDisk();
            if (zipFiles.contains(zipPath)) {
                int counter = 0;
                while (zipFiles.contains(counter + zipPath)) {
                    counter++;
                }
                zipPath = counter + zipPath;
            }
            toZip.add(new FileSource(zipPath, new File(SpreadsheetService.getHomeDir() + ImportService.dbPath + loggedInUser.getBusinessDirectory() + ImportService.importTemplatesDir + importTemplate.getFilenameForDisk())));
            zipFiles.add(zipPath);
        }
        // now the typed uploads, need to clearly mark them as such
        for (List<UploadRecord> forType : groupedByFileType.values()) {
            for (UploadRecord uploadRecord : forType) {
                // this little chunk to stop duplicate entries
                String zipPath = uploadRecord.getFileType() + TYPEBREAK + uploadRecord.getFileName();
                if (zipFiles.contains(zipPath)) {
                    int counter = 0;
                    while (zipFiles.contains(counter + zipPath)) {
                        counter++;
                    }
                    zipPath = counter + zipPath;
                }
                toZip.add(new FileSource(zipPath, new File(uploadRecord.getTempPath())));
                zipFiles.add(zipPath);
            }
        }

        String dbName = loggedInUser.getDatabase().getName();
        if (dbName.length() < 3) dbName = dbName + "xx";
        File tempzip = File.createTempFile(dbName, ".zip");
        System.out.println("temp zip " + tempzip.getPath());
        ZipEntrySource[] zes = new ZipEntrySource[toZip.size()];
        toZip.toArray(zes);
        ZipUtil.pack(zes, tempzip);
        tempzip.deleteOnExit();
        if (dbFile != null) {
            dbFile.delete();// may as well clear it up now
        }

        return tempzip;
    }

    public static String loadBackup(LoggedInUser loggedInUser, File file, String database) throws Exception {
        //long time = System.currentTimeMillis();
        StringBuilder toReturn = new StringBuilder();
        System.out.println("attempting backup restore on " + file.getPath());
        ZipUtil.explode(file);
        // after exploding the original file is replaced with a directory
        File zipDir = new File(file.getPath());
        File[] files = zipDir.listFiles();
        // need to load the database first
        boolean dbRestored = false;
        for (File f : files) {
            if (f.getName().endsWith(".db")) {
                loadDBBackup(loggedInUser, f, database, toReturn, false);
                dbRestored = true;
            }
        }
        // I'm going to allow backup restores on just the reports, as we're trying to enable batch report and import template versioning
        if (!dbRestored) {
            // some copying from Admin service
            Database db = DatabaseDAO.findForNameAndBusinessId(database, loggedInUser.getUser().getBusinessId());
            if (db != null) {
                loggedInUser.setDatabaseWithServer(DatabaseServerDAO.findById(db.getDatabaseServerId()), db);
                final List<OnlineReport> reports = OnlineReportDAO.findForDatabaseId(db.getId());
                DatabaseReportLinkDAO.unLinkDatabase(db.getId());
                for (OnlineReport or : reports) {
                    if (DatabaseReportLinkDAO.getDatabaseIdsForReportId(or.getId()).isEmpty() && UserDAO.findForReportId(or.getId()).isEmpty()) { // then this report no longer has any databases
                        AdminService.removeReportByIdWithBasicSecurity(loggedInUser, or.getId());
                    }
                }
                // if there is a template associated with this database and no others then zap it
                ImportTemplate importTemplate = db.getImportTemplateId() != -1 ? ImportTemplateDAO.findById(db.getImportTemplateId()) : null;
                if (importTemplate != null) {
                    if (DatabaseDAO.findForImportTemplateId(importTemplate.getId()).size() == 1) {
                        ImportTemplateDAO.removeById(importTemplate);
                        Files.deleteIfExists(Paths.get(SpreadsheetService.getHomeDir() + dbPath + loggedInUser.getBusinessDirectory() + importTemplatesDir + importTemplate.getFilenameForDisk()));
                    }
                }
            }
        }

        // now reports
        for (File f : files) {
            if (!f.getName().endsWith(".db")) {
                // deal with the moved (typed) uploads first
                if (f.getName().contains(TYPEBREAK)) {
                    // fragments of the code to make an upload record without actually uploading
                    String type = f.getName().substring(0, f.getName().indexOf(TYPEBREAK));
                    String fileName = f.getName().substring(f.getName().indexOf(TYPEBREAK) + TYPEBREAK.length());
                    String uploadFilePath = SpreadsheetService.getHomeDir() + "/temp/" + System.currentTimeMillis() + fileName;
                    Files.copy(Paths.get(f.getPath()), Paths.get(uploadFilePath));// timestamp to stop file overwriting
                    UploadRecord uploadRecord = new UploadRecord(0, LocalDateTime.now(), loggedInUser.getUser().getBusinessId()
                            , loggedInUser.getDatabase().getId(), loggedInUser.getUser().getId()
                            , fileName, type, "From backup restore", uploadFilePath, null);
                    UploadRecordDAO.store(uploadRecord);

                } else { // report or template. Or data now, setup etc
                    // rename the xlsx file to get rid of the ID that will probably be in front in the backup zip
                    String fileName;
                    if (f.getName().contains("-") && NumberUtils.isNumber(f.getName().substring(0, f.getName().indexOf("-")))) {
                        fileName = f.getName().substring(f.getName().indexOf("-") + 1);
                    } else {
                        fileName = f.getName();
                    }
                    // hacky way of dealing with categories
                    String category = null;
                    if (fileName.contains(CATEGORYBREAK)) {
                        category = fileName.substring(0, fileName.indexOf(CATEGORYBREAK));
                        fileName = f.getName().substring(fileName.indexOf(CATEGORYBREAK) + CATEGORYBREAK.length());
                    }
                    if (fileName.contains(CATEGORYBREAKOLD)) {
                        category = fileName.substring(0, fileName.indexOf(CATEGORYBREAKOLD));
                        fileName = f.getName().substring(fileName.indexOf(CATEGORYBREAKOLD) + CATEGORYBREAKOLD.length());
                    }
                    List<UploadedFile> uploadedFiles = ImportService.importTheFile(loggedInUser
                            , new UploadedFile(f.getAbsolutePath(), Collections.singletonList(fileName), false), null, null);
                    // EFC : got to hack the category in, I don't like this . . .
                    if (category != null) {
                        for (UploadedFile uploadedFile : uploadedFiles) {
                            if (uploadedFile.getReportName() != null) {
                                OnlineReport or = OnlineReportDAO.findForNameAndUserId(uploadedFile.getReportName(), loggedInUser.getUser().getId());
                                if (or != null) {
                                    or.setCategory(category);
                                    OnlineReportDAO.store(or);
                                }
                            }
                        }
                    }
                    toReturn.append(ManageDatabasesController.formatUploadedFiles(uploadedFiles, -1, false, null)).append("<br/>");
                }
            }
        }
/*        long secondstaken = (System.currentTimeMillis() - time) / 1000;
        System.gc();
        final Runtime runtime = Runtime.getRuntime();
        final int mb = 1024 * 1024;
        NumberFormat nf = NumberFormat.getInstance();
        // variable here is a bit easier to read and makes intellij happier
        System.out.println("--- MEMORY USED :  " + nf.format((runtime.totalMemory() - runtime.freeMemory()) / mb) + "MB of " + nf.format(runtime.totalMemory() / mb) + "MB, max allowed " + nf.format(runtime.maxMemory() / mb));
        System.out.println("time taken to restore backup : " + secondstaken);*/

        return toReturn.toString();
    }

    // should be in shared?
    private static int batchSize = 100_000;

    public static void loadDBBackup(LoggedInUser loggedInUser, File file, String database, StringBuilder log, boolean justEmpty) {
        int line = 1;
        try {
            CsvMapper csvMapper = new CsvMapper();
            csvMapper.enable(CsvParser.Feature.WRAP_AS_ARRAY);
            CsvSchema schema = csvMapper.schemaFor(String[].class)
                    .withColumnSeparator('\t')
                    .withLineSeparator("\n")
                    .withoutQuoteChar();
            MappingIterator<String[]> lineIterator = csvMapper.readerFor(String[].class).with(schema).readValues(
                    new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8));
            String[] lineValues = lineIterator.next();
            line++;
            if (database == null || database.trim().isEmpty()) {
                database = lineValues[0];
            }
            // first thing to do is delete the database and all associated reports
            Database db = DatabaseDAO.findForNameAndBusinessId(database, loggedInUser.getUser().getBusinessId());
            if (db != null) {
                // hack in a security check, we know the user is an Admin or Developer but if a developer it needs to be their DB
                if (loggedInUser.getUser().isDeveloper() && loggedInUser.getUser().getId() != db.getUserId()) {
                    throw new Exception("A developer cannot restore to a database that is not theirs");
                }
                // here's a question - given the hack to move the database id should it just empty instead of delete and delete reports as necessary? Not a biggy but todo
                if (justEmpty) {
                    loggedInUser.setDatabaseWithServer(DatabaseServerDAO.findById(db.getDatabaseServerId()), db);
                    AdminService.emptyDatabase(loggedInUser);
                } else {
                    AdminService.removeDatabaseByIdWithBasicSecurity(loggedInUser, db.getId());
                    Database createdDb = AdminService.createDatabase(db.getName(), loggedInUser, DatabaseServerDAO.findById(db.getDatabaseServerId()));
                    // fix the user records if we're overwriting a db
                    List<User> users = UserDAO.findForDatabaseId(db.getId());
                    for (User u : users) {
                        u.setDatabaseId(createdDb.getId());
                        UserDAO.store(u);
                    }
                }
            } else {
                // still not really dealing with different servers properly
                AdminService.createDatabase(database, loggedInUser, loggedInUser.getDatabaseServer());
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

    // todo - use Path for paths? applies everywhere!
    public static File createDBBackupFile(String databaseName, String subsetName, DatabaseAccessToken databaseAccessToken, DatabaseServer databaseServer) throws Exception {
        String path = RMIClient.getServerInterface(databaseServer.getIp()).getBackupFileForDatabase(databaseName, subsetName, databaseAccessToken);
        File file;
        if (!databaseServer.getIp().equals(ImportService.LOCALIP)) {// the call via RMI is the same the question is whether the path refers to this machine or another
            String url = databaseServer.getSftpUrl();
            int slashAfterAt = url.indexOf( "/", url.indexOf("@"));
            url = url.substring(0, slashAfterAt);
            file = SFTPUtilities.copyFromDatabaseServer(url + path, true);
        } else {
            file = new File(path);
        }
        return file;
    }
}
