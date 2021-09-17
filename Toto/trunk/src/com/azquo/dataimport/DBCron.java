package com.azquo.dataimport;

import com.azquo.admin.BackupService;
import com.azquo.admin.business.Business;
import com.azquo.admin.business.BusinessDAO;
import com.azquo.admin.database.*;
import com.azquo.admin.user.User;
import com.azquo.memorydb.DatabaseAccessToken;
import com.azquo.spreadsheet.LoggedInUser;
import com.azquo.spreadsheet.SpreadsheetService;
import com.azquo.spreadsheet.transport.UploadedFile;
import com.extractagilecrm.ExtractContacts;
import com.extractappointedd.ExtractAppointedd;
import org.apache.commons.lang.math.NumberUtils;
import org.springframework.scheduling.annotation.Scheduled;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.zeroturnaround.zip.commons.FileUtils;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/**
 * Copyright (C) 2016 Azquo Ltd.
 * <p>
 * Created by cawley on 20/04/15.
 * <p>
 * Currently unused but it may be useful so I'm leaving it here
 */
public class DBCron {

    private static final String dbBackupsDirectory = "DBBACKUPS";

    public static final String AZQUODATABASEPERSISTENCENAME = "AZQUODATABASEPERSISTENCENAME";

    @Scheduled(cron = "0 * * * * *")
    public void demoServiceMethod() {
//        System.out.println("every minute?" + LocalDateTime.now());
    }

    // every 5 mins, check imports that may need to be run then
    @Scheduled(cron = "0 */5 * * * *")
    public static void check5MinsImport() {
        Path cronDir = Paths.get(SpreadsheetService.getHomeDir() + "/cron");
        if (Files.exists(cronDir)) {
            try (Stream<Path> list = Files.list(cronDir)) {
                list.forEach(path -> {
                    // Do stuff
                    if (!Files.isDirectory(path) && path.getFileName().toString().toLowerCase().startsWith("5mins")) { // skip any directories
                        runCronFile(path);
                    }
                });
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // every hour, check imports that may need to be run then
    @Scheduled(cron = "0 0 * * * *")
    public static void checkHourlyImport() {
        Path cronDir = Paths.get(SpreadsheetService.getHomeDir() + "/cron");
        if (Files.exists(cronDir)) {
            try (Stream<Path> list = Files.list(cronDir)) {
                list.forEach(path -> {
                    // Do stuff
                    if (!Files.isDirectory(path) && path.getFileName().toString().toLowerCase().startsWith("hourly")) { // skip any directories
                        runCronFile(path);
                    }
                });
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // every day, check imports that may need to be run then
    @Scheduled(cron = "0 0 0 * * *")
    public static void checkDailyImport() {
        Path cronDir = Paths.get(SpreadsheetService.getHomeDir() + "/cron");
        if (Files.exists(cronDir)) {
            try (Stream<Path> list = Files.list(cronDir)) {
                list.forEach(path -> {
                    // Do stuff
                    if (!Files.isDirectory(path) && path.getFileName().toString().toLowerCase().startsWith("daily")) { // skip any directories
                        runCronFile(path);
                    }
                });
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static void runCronFile(Path path){
        try {
            List<String> config = Files.readAllLines(path, Charset.defaultCharset());
            String type = config.get(0);
            String destination = config.get(1);
            String baseUrl = config.get(2);
            String userEmail = config.get(3);
            String restAPIKey = config.get(4);
            if (type.startsWith("agilecontacts")){
                int since = Integer.parseInt(type.substring(type.indexOf("-") + 1));
                ExtractContacts.extractContacts(baseUrl, userEmail, restAPIKey, destination, since);
            }
            if (type.startsWith("agiledeals")){
                ExtractContacts.extractDeals(baseUrl, userEmail, restAPIKey, destination);
            }
            if (type.startsWith("appointeddcustomers")){
                ExtractAppointedd.extract(baseUrl,"/v1/customers", restAPIKey, destination);
            }
            if (type.startsWith("appointeddresources")){
                ExtractAppointedd.extract(baseUrl,"/v1/resources", restAPIKey, destination);
            }
            if (type.startsWith("appointeddbookings")){
                ExtractAppointedd.extract(baseUrl,"/v1/bookings", restAPIKey, destination);
            }
        } catch (IOException e) {

        }

    }

    @Scheduled(cron = "0 */15 * * * *")// 15 mins as per ed broking requirements
    public void autoDBBakups() throws Exception {
        System.out.println("backup check " + LocalDateTime.now());
        List<Database> forBackup = DatabaseDAO.findForAutoBackup();
        for (Database toBackUp : forBackup) {
            // initially put the backup code in here, can factor it off later
//            String backupname = RMIClient.getServerInterface(DatabaseServerDAO.findpById(toBackUp.getDatabaseServerId()).getIp()).getMostRecentProvenance(toBackUp.getPersistenceName());
            String backupname = toBackUp.getLastProvenance();
            // make backup name "safer" for file names?
            // could a name in theory clash?
            if (backupname != null && backupname.length() > 0) {
                backupname = backupname.replaceAll("[^A-Za-z0-9_]", "");
                /* ok where do the backups go?
                 this is how reports are found String bookPath = SpreadsheetService.getHomeDir() + ImportService.dbPath + loggedInUser.getBusinessDirectory() + ImportService.onlineReportsDir + finalOnlineReport.getFilenameForDisk();
                 this is a directory per business though . . . we want a directory per database really. So make a directory in there! Need to zap on delete . . .
                 */
                String dbBackupsDir = getDBBackupsDirectory(toBackUp);
                if (!Files.exists(Paths.get(dbBackupsDir + "/" + backupname))) { // then we need to make a backup . . .
                    DatabaseServer server = DatabaseServerDAO.findById(toBackUp.getDatabaseServerId());
                    File dbBackupFile = BackupService.createDBBackupFile(toBackUp.getName(), null, new DatabaseAccessToken("", "", server.getIp(), toBackUp.getPersistenceName()), server);
                    FileUtils.copyFile(dbBackupFile, new File(dbBackupsDir + "/" + backupname));
                    dbBackupFile.delete();
                }
            }
        }
    }

    public static String getDBBackupsDirectory(Database toBackUp) throws IOException {
        Business b = BusinessDAO.findById(toBackUp.getBusinessId());
        String businessDir;
        // put in switch to use config on the backup dir
        if (SpreadsheetService.getDBBackupDir() != null) {
            businessDir = SpreadsheetService.getDBBackupDir() + b.getBusinessDirectory();
        } else {
            businessDir = SpreadsheetService.getHomeDir() + ImportService.dbPath + b.getBusinessDirectory();
        }
        String dbBackupsDir = businessDir + "/" + dbBackupsDirectory + toBackUp.getPersistenceName(); // persistence name less likely to cause a problem with the file system
        Files.createDirectories(Paths.get(dbBackupsDir));
        return dbBackupsDir;
    }

    // need to think of how to define this
    @Scheduled(cron = "0 * * * * *")
    public void directoryScan() throws Exception {
        final long millisOldThreshold = 300_000; // must be at least 5 mins old. Don't catch a file that's being transferred
        synchronized (this) { // one at a time
            if (SpreadsheetService.getXMLScanDir() != null && SpreadsheetService.getXMLScanDir().length() > 0) {
                // make tagged as before but this time the plan is to parse all found XML files into a single CSV and upload it
                // note - have moved tagged to temp as on Ed Broking's servers the scan dir is a remote network drive, don't want to do "work" in there
                Path tagged = Paths.get(SpreadsheetService.getHomeDir() + "/temp/tagged");
                if (!Files.exists(tagged)) {
                    Files.createDirectories(tagged);
                }
                // extra bit of logic. It seems Brokasure process files *slow* so I need to check that the newest file is at least 5 mins old or it could be in the middle of a batch
                // fragment off t'internet to get the most recent file
                Path p = Paths.get(SpreadsheetService.getXMLScanDir());
                // need to do try with resources or it leaks file handlers
                try (Stream<Path> list1 = Files.list(p)) {
                    Optional<Path> lastFilePath = list1    // here we get the stream with full directory listing
                            .filter(f -> (!Files.isDirectory(f) && f.getFileName().toString().endsWith("xml")))  // exclude subdirectories and non xml files from listing
                            .max(Comparator.comparingLong(f -> f.toFile().lastModified()));  // finally get the last file using simple comparator by lastModified field
                    // 300 seconds, 5 minutes, I want the most recent file to be at least that old before I start doing things to them
                    if (lastFilePath.isPresent() && (System.currentTimeMillis() - Files.getLastModifiedTime(lastFilePath.get()).toMillis()) > millisOldThreshold) {
                        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                        final DocumentBuilder builder = factory.newDocumentBuilder();
                        long fileMillis = System.currentTimeMillis();
                        //if (lastModifiedTime.toMillis() < (timestamp - 120_000)) {
                        // I'm going to allow for the possiblity that different files might have different fields
                        Set<String> headings = new HashSet<>();
                        headings.add("Date");
                        headings.add("Error");

                        Map<String, Map<String, String>> filesValues = new HashMap<>();// filename, values
                        AtomicReference<String> rootDocumentName = new AtomicReference<>();
                        try (Stream<Path> list = Files.list(p)) {
                            list.forEach(path -> {
                                // Do stuff
                                if (!Files.isDirectory(path)) { // skip any directories
                                    try {
                                            /*

                                            Note : I was assuming files being returned in pairs but it seems not,
                                            if the file name contains error then look for the original in temp and load that as well

                                             */

                                        String origName = path.getFileName().toString();
                                        if (origName.toLowerCase().contains(".xml")) {
                                            String fileKey = origName.substring(0, origName.indexOf("-"));
                                            boolean error = origName.toLowerCase().contains("error");
                                            FileTime lastModifiedTime = Files.getLastModifiedTime(path);
                                            // todo - match to the source file when it hits an error response

                                            long timestamp = System.currentTimeMillis();
                                            System.out.println("file : " + origName);
                                            // newer logic, start with the original sent data then add anything from brokasure on. Will help Bill/Nic to parse
                                            // further to this we'll only process files that have a corresponding temp file as Dev and UAT share directories so if there's no matching file in temp don't do anything
                                            if (Files.exists(Paths.get(SpreadsheetService.getHomeDir() + "/temp/" + fileKey + ".xml"))) {
                                                readXML(fileKey, filesValues, null, builder, Paths.get(SpreadsheetService.getHomeDir() + "/temp/" + fileKey + ".xml"), headings, lastModifiedTime);
                                                // todo what if root tags don't match between the existing file and the one from BS??
                                                // add in extra info, initial reason it was required was for section info not suitable for Brokasure but required to load the data back in
                                                if (Files.exists(Paths.get(SpreadsheetService.getHomeDir() + "/temp/" + fileKey + ".properties"))) {
                                                    try (InputStream is = new FileInputStream(SpreadsheetService.getHomeDir() + "/temp/" + fileKey + ".properties")) {
                                                        Properties properties = new Properties();
                                                        properties.load(is);
                                                        Map<String, String> thisFileValues = filesValues.computeIfAbsent(fileKey, t -> new HashMap<>());
                                                        for (String propertyName : properties.stringPropertyNames()) {
                                                            headings.add(propertyName);
                                                            thisFileValues.put(propertyName, properties.getProperty(propertyName));
                                                        }
                                                    }
                                                }
                                                // ok I need to stop fields of a different type mixing, read xml will return false if the root document name doesn't match. Under those circumstances leave the file there
                                                if (readXML(fileKey, filesValues, rootDocumentName, builder, path, headings, lastModifiedTime)) {
                                                    Files.move(path, tagged.resolve(timestamp + origName));
                                                }
                                                // check the xlsx isn't still in the inbox - zap it if it is
                                                Path leftoverXLSX = Paths.get(SpreadsheetService.getXMLDestinationDir()).resolve(fileKey.toLowerCase() + ".xlsx");
                                                if (Files.exists(leftoverXLSX)) {
                                                    Files.delete(leftoverXLSX);
                                                } else if (fileKey.toLowerCase().startsWith("cs") && !error) { // it was zapped (as in ok!) - in the case of CS claim settlements the original file which will be in temp now needs to go in the outbox - if there was no error of course!
                                                    Path xlsxFileToMove = Paths.get(SpreadsheetService.getHomeDir() + "/temp/" + fileKey.toLowerCase() + ".xlsx");
                                                    if (Files.exists(xlsxFileToMove)) {
                                                        System.out.println("moving file back to the out box " + fileKey.toLowerCase() + ".xlsx");
                                                        Files.move(xlsxFileToMove, Paths.get(SpreadsheetService.getXMLScanDir()).resolve(fileKey.toLowerCase() + ".xlsx"));
                                                    }
                                                }
                                            } else {
                                                System.out.println("Can't find corresponding temp xml file " + fileKey + ".xml, perhaps it was generated by another server");
                                            }
                                        } else {
                                            // supress this for the mo as we're putting xlsx files back in here. Todo - clarify what's going on there!
                                            //System.out.println("non XML file found?? " + origName);
                                        }
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                        // try to move the file if it failed - unclog the outbox
                                        try {
                                            Files.move(path, Paths.get(SpreadsheetService.getHomeDir() + "/temp/" + path.getFileName()));
                                        } catch (IOException ex) {
                                            ex.printStackTrace();
                                        }
                                    }
                                }
                            });
                        }
                        // dev = allri_risksolutions
                        // uat = edbr_risksolutions
                        // prod = edbr_risksolutions
                        // these may change, we're only concerned with a hopefully small possibility of XML being sent by the old code and being picked up by this new code
                        // , in that case default the database. edbr_risksolutions wins.
                        final String defaultAzquoDatabasePersistenceName = "edbr_risksolutions";

                        if (!filesValues.isEmpty()) {
                            // now Hanover has been added there's an issue that files can come back and go in different databases
                            // so we may need to make more than one file for a block of parsed files, batch them up
                            Map<String, List<Map<String, String>>> linesByDatabase = new HashMap<>(); // could go koloboke, not bothered at the mo
                            for (Map<String, String> lineValues : filesValues.values()) {
                                if (lineValues.get(AZQUODATABASEPERSISTENCENAME) != null && !lineValues.get(AZQUODATABASEPERSISTENCENAME).isEmpty()) { // could it be empty?
                                    linesByDatabase.computeIfAbsent(lineValues.get(AZQUODATABASEPERSISTENCENAME), t -> new ArrayList<>()).add(lineValues); // single threaded this should be fine
                                } else {
                                    linesByDatabase.computeIfAbsent(defaultAzquoDatabasePersistenceName, t -> new ArrayList<>()).add(lineValues);
                                }
                            }
                            for (String databasePersistenceName : linesByDatabase.keySet()) {
                                List<Map<String, String>> lines = linesByDatabase.get(databasePersistenceName);
                                // base the file name off the db name also
                                String csvFileName = fileMillis + "-" + databasePersistenceName + " (importtemplate=BrokasureTemplates;importversion=Brokasure" + rootDocumentName.get() + ").tsv";
                                BufferedWriter bufferedWriter = Files.newBufferedWriter(tagged.resolve(csvFileName), StandardCharsets.UTF_8);
                                for (String heading : headings) {
                                    bufferedWriter.write(heading + "\t");
                                }
                                bufferedWriter.newLine();
                                for (Map<String, String> lineValues : lines) {
                                    for (String heading : headings) {
                                        String value = lineValues.get(heading);
                                        bufferedWriter.write((value != null ? value : "") + "\t");
                                    }
                                    bufferedWriter.newLine();
                                }
                                bufferedWriter.close();
                                Path newScannedDir = Files.createDirectories(tagged.resolve(fileMillis + "scanned"));
                                try (Stream<Path> list = Files.list(tagged)) {
                                    list.forEach(path -> {
                                        // Do stuff
                                        if (!Files.isDirectory(path)) { // skip any directories
                                            try {
                                                Files.move(path, newScannedDir.resolve(path.getFileName()));
                                            } catch (IOException e) {
                                                e.printStackTrace();
                                            }
                                        }
                                    });
                                }

                                Database db = DatabaseDAO.findForPersistenceName(databasePersistenceName);
                                if (Files.exists(newScannedDir.resolve(csvFileName)) && db != null) { // it should exist and a database should be set also
                                    Business b = BusinessDAO.findById(db.getBusinessId());
                                    LoggedInUser loggedInUser = new LoggedInUser(new User(0, LocalDateTime.now(), b.getId(), "brokasure", "", "", "", "", "", 0, 0, "", "")
                                            , DatabaseServerDAO.findById(db.getDatabaseServerId()), db, null, b);
                                    final Map<String, String> fileNameParams = new HashMap<>();
                                    String fileName = newScannedDir.resolve(csvFileName).getFileName().toString();
                                    ImportService.addFileNameParametersToMap(fileName, fileNameParams);

                                    ImportService.importTheFile(loggedInUser, new UploadedFile(newScannedDir.resolve(csvFileName).toString()
                                            , new ArrayList<>(Collections.singletonList(fileName)), fileNameParams, false, false), null, null);
                                }
                            }
                        }
                    }
                }
            }

            // scan for direct uploads - added for Joe Brown's
            // millis old for JB can be longer? We'll see . . .
            if (SpreadsheetService.getDirectUploadDir() != null && !SpreadsheetService.getDirectUploadDir().isEmpty()) {
                Path directUploadDir = Paths.get(SpreadsheetService.getDirectUploadDir());
                if (directUploadDir.toFile().exists() && directUploadDir.toFile().isDirectory()) {
                    try (Stream<Path> directUploadDirList = Files.list(directUploadDir)) {
                        Iterator<Path> directUploadDirListIterator = directUploadDirList.iterator();
                        // go through the main directory looking for directories that match a DB name
                        while (directUploadDirListIterator.hasNext()) {
                            Path dbDir = directUploadDirListIterator.next();
                            File dbDirFile = dbDir.toFile();
                            if (dbDirFile.isDirectory()) {
                                Database matchingDBdir = DatabaseDAO.findForPersistenceName(dbDirFile.getName());
                                if (matchingDBdir != null) {
                                    // ok we have a database directory
                                    final Business b = BusinessDAO.findById(matchingDBdir.getBusinessId());
                                    try (Stream<Path> filesToUpload = Files.list(dbDir)) {
                                        Iterator<Path> filesToUploadIterator = filesToUpload.iterator();
                                        while (filesToUploadIterator.hasNext()) {
                                            Path fileToUpload = filesToUploadIterator.next();
                                            if (fileToUpload.toFile().isFile()) {
                                                FileTime lastModifiedTime = Files.getLastModifiedTime(fileToUpload);
                                                // only interested in files in the db directory and ones that have not very recently been modified - don't want to interfere with a file while it's being copied or generated
                                                if (lastModifiedTime.toMillis() < (System.currentTimeMillis() - millisOldThreshold)) {
                                                    Path moved = Paths.get(SpreadsheetService.getHomeDir() + "/temp/" + System.currentTimeMillis() + fileToUpload.getFileName());
                                                    String fileName = fileToUpload.toFile().getName();
                                                    Files.move(fileToUpload, moved);
                                                    // now . . .we need a user to upload it. Initially copying broaksure code above, this may need to be changed in time
                                                    LoggedInUser loggedInUser = new LoggedInUser(new User(0, LocalDateTime.now(), b.getId(), "brokasure", "", "", "", "", "", 0, 0, "", "")
                                                            , DatabaseServerDAO.findById(matchingDBdir.getDatabaseServerId()), matchingDBdir, null, b);
                                                    final Map<String, String> fileNameParams = new HashMap<>();
                                                    ImportService.addFileNameParametersToMap(fileName, fileNameParams);
                                                    ImportService.importTheFile(loggedInUser, new UploadedFile(moved.toString()
                                                            , new ArrayList<>(Collections.singletonList(fileName)), fileNameParams, false, false), null, null);
                                                    // ok I know this seems illogical after moving it, this rule was added after
                                                    Files.deleteIfExists(moved);
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // factored as it will be called a second time for errors
    public static boolean readXML(String fileKey, Map<String, Map<String, String>> filesValues, AtomicReference<String> rootDocumentName
            , DocumentBuilder builder, Path path, Set<String> headings, FileTime lastModifiedTime) throws IOException, SAXException {
        // unlike the above, before moving it I need to read it
        Map<String, String> thisFileValues = filesValues.computeIfAbsent(fileKey, t -> new HashMap<>());
        Document workbookXML = builder.parse(path.toFile());
        //workbookXML.getDocumentElement().normalize(); // probably fine on smaller XML, don't want to do on the big stuff - note I'd commented this but not sure why on this small XML. Leave for the mo as it's working but the tracking stuff needs it
        Element documentElement = workbookXML.getDocumentElement();
        if (rootDocumentName != null) { // when loading non brokasure files i.e. the originals generated then don't pay attention to the root name - it might be wrong
            if (rootDocumentName.get() == null) {
                rootDocumentName.set(documentElement.getTagName());
            } else if (!rootDocumentName.get().equals(documentElement.getTagName())) { // this file doens't match the others in this set, don't scan it into the file
                filesValues.remove(fileKey); // get rid of that key
                return false; // tells the code outside to leave the file there, we'lll process later
            }
        }
        // this criteria is currently suitable for the simple XML from Brokasure
        for (int index = 0; index < documentElement.getChildNodes().getLength(); index++) {
            Node node = documentElement.getChildNodes().item(index);
            if (node.hasChildNodes()) {
                headings.add(node.getNodeName());
                thisFileValues.put(node.getNodeName(), node.getFirstChild().getNodeValue().replace("\n", ""));
            }
        }
        thisFileValues.put("Date", lastModifiedTime.toString());
        return true;
    }

    AtomicBoolean reportsRunning = new AtomicBoolean(false);

    @Scheduled(cron = "0 * * * * *")
    public void runScheduledReports() {
        // was going synchronised but this may trip up due to a slight delay in mysql updating. Instead go atomic boolean, if one is running just don't try. Wait another minute.
        if (reportsRunning.compareAndSet(false, true)) {
            try {
                SpreadsheetService.runScheduledReports();
            } catch (Exception e) {
                e.printStackTrace();
            }
            reportsRunning.set(false);
        }

    }

    // r154 - claims from tracking db
    // claims transmission
    // E4 always UTR?
    // E5 update further columns, UTR again
    // more on the specify?
    // e4 sumbitted to class
    // e5 = messqage from class
    // e6 = message back from the underwriters
    // utr, e5 error description, e6 desc texts
    // e5 all data error
    @Scheduled(cron = "0 */5 * * * *")// 5 minutes?
    public void extractEdBrokingTrackingData() throws Exception {
        synchronized (this) {
            String transNo = null;// may be added back in as a parameter later
            String trackingdb = SpreadsheetService.getTrackingDb();
            String trackingTable = SpreadsheetService.getTrackingTable();
            if (trackingdb != null && !trackingdb.trim().isEmpty() && trackingTable != null && !trackingTable.trim().isEmpty()) {
                Path tracking = Paths.get(SpreadsheetService.getHomeDir() + "/temp/tracking");
                if (!Files.exists(tracking)) {
                    Files.createDirectories(tracking);
                }
                int maxKey = 2_200_000;
                List<Map<String, String>> rows;
                if (transNo != null) {
                    rows = TrackingParser.findForTransactionNo(transNo);
                } else {
                    try (Stream<Path> list1 = Files.list(tracking)) {
                        Optional<Path> lastFilePath = list1    // here we get the stream with full directory listing
                                .filter(f -> !Files.isDirectory(f))  // exclude subdirectories and non xml files from listing
                                .max(Comparator.comparingLong(f -> f.toFile().lastModified()));  // finally get the last file using simple comparator by lastModified field
                        if (lastFilePath.isPresent()) {
                            String fileName = lastFilePath.get().getFileName().toString();
                            int trackingIndex = fileName.indexOf("tracking");
                            if (trackingIndex > 0 && NumberUtils.isDigits(fileName.substring(0, trackingIndex))) {
                                maxKey = Integer.parseInt(fileName.substring(0, trackingIndex));
                            }
                        }
                    }
                    rows = TrackingParser.findGreaterThan(maxKey);
                }

                if (!rows.isEmpty()) {
                    System.out.println("running tracking db update");
                    // I don't think I can use the other XMl code, too different
                    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                    final DocumentBuilder builder = factory.newDocumentBuilder();
                    List<Map<String, StringBuilder>> premiumsFilesValues = new ArrayList<>();
                    List<Map<String, StringBuilder>> claimsFilesValues = new ArrayList<>();
                    Collection<String> cheadings = new HashSet<>();
                    Collection<String> pheadings = new HashSet<>();
                    for (Map<String, String> row : rows) {
                        Map<String, StringBuilder> thisFileValues = new HashMap<>();
                        for (String col : row.keySet()) {
                            if (col.equals(TrackingParser.TRACKMESSKEY)) {
                                int trackMessKey = Integer.parseInt(row.get(col));
                                if (trackMessKey > maxKey) {
                                    maxKey = trackMessKey;
                                }
                            }
                            if (col.equals(TrackingParser.TMXMLDATA)) {
                                Document xml = builder.parse(new InputSource(new StringReader(row.get(col))));
                                xml.normalizeDocument();
                                Element documentElement = xml.getDocumentElement();
                                for (int index = 0; index < documentElement.getChildNodes().getLength(); index++) {
                                    Node node = documentElement.getChildNodes().item(index);
                                    if (node.hasChildNodes()) {
                                        if (node.getChildNodes().getLength() > 1) {
                                            for (int index1 = 0; index1 < node.getChildNodes().getLength(); index1++) {
                                                Node node1 = node.getChildNodes().item(index1);
                                                if (!node1.getNodeName().equalsIgnoreCase("#text") && node1.getFirstChild() != null) {
                                                    // I know this is getting convoluted to get three levels down. If it goes one more I'll need a generic solution
                                                    if (node1.getChildNodes().getLength() > 1) {
                                                        for (int index2 = 0; index2 < node1.getChildNodes().getLength(); index2++) {
                                                            Node node2 = node1.getChildNodes().item(index2);
                                                            if (!node2.getNodeName().equalsIgnoreCase("#text") && node2.getFirstChild() != null) {
                                                                if (thisFileValues.get(node.getNodeName() + "-" + node1.getNodeName() + "-" + node2.getNodeName()) == null) {
                                                                    thisFileValues.put(node.getNodeName() + "-" + node1.getNodeName() + "-" + node2.getNodeName(), new StringBuilder(node2.getFirstChild().getNodeValue()));
                                                                } else {
                                                                    thisFileValues.get(node.getNodeName() + "-" + node1.getNodeName() + "-" + node2.getNodeName()).append("\n").append(node2.getFirstChild().getNodeValue());
                                                                }
                                                            }

                                                        }
                                                    } else {
                                                        if (thisFileValues.get(node.getNodeName() + "-" + node1.getNodeName()) == null) {
                                                            thisFileValues.put(node.getNodeName() + "-" + node1.getNodeName(), new StringBuilder(node1.getFirstChild().getNodeValue()));
                                                        } else {
                                                            thisFileValues.get(node.getNodeName() + "-" + node1.getNodeName()).append("\n").append(node1.getFirstChild().getNodeValue());
                                                        }
                                                    }
                                                }
                                            }
                                        } else {
                                            if (thisFileValues.get(node.getNodeName()) == null) {
                                                thisFileValues.put(node.getNodeName(), new StringBuilder(node.getFirstChild().getNodeValue()));
                                            } else {
                                                thisFileValues.get(node.getNodeName()).append("\n").append(node.getFirstChild().getNodeValue());
                                            }
                                        }
                                    }
                                }
                            } else {
                                if (row.get(col) != null) {
                                    thisFileValues.put(col, new StringBuilder(row.get(col)));
                                }
                            }
                        }
                        if (thisFileValues.get(TrackingParser.TMVARCAT3) != null) {
                            if ("Claims Tracking".equals(thisFileValues.get(TrackingParser.TMVARCAT3).toString())) {
                                claimsFilesValues.add(thisFileValues);
                                cheadings.addAll(thisFileValues.keySet());
                            }
                            if ("Premium".equals(thisFileValues.get(TrackingParser.TMVARCAT3).toString())
                                    || "Bureau Submission".equals(thisFileValues.get(TrackingParser.TMVARCAT3).toString())
                                    || "Client Submission".equals(thisFileValues.get(TrackingParser.TMVARCAT3).toString())) {
                                premiumsFilesValues.add(thisFileValues);
                                pheadings.addAll(thisFileValues.keySet());
                            }
                        }
                    /*else {
                    }*/
//                    System.out.println("key " + tp.getFirst());
//                    System.out.println("xml " + tp.getSecond());

                    }
                    // crude initial version - try to load into any database with an import template attached that has a claims tracking sheet
                    if (!claimsFilesValues.isEmpty()) {
                        String csvFileName = (transNo != null ? transNo : maxKey) + "tracking (importversion=ClaimsTracking).tsv";
                        BufferedWriter bufferedWriter = Files.newBufferedWriter(tracking.resolve(csvFileName), StandardCharsets.UTF_8);
                        cheadings = new ArrayList<>(cheadings);
                        pheadings = new ArrayList<>(pheadings);
                        for (String heading : cheadings) {
                            bufferedWriter.write(heading + "\t");
                        }
                        bufferedWriter.newLine();
                        for (Map<String, StringBuilder> row : claimsFilesValues) {
                            for (String heading : cheadings) {
                                StringBuilder value = row.get(heading);
                                // using isconvertedfromworksheet will then preserve these characters in the system, I think we might need them
                                bufferedWriter.write((value != null ? value.toString().replace("\n", "\\\\n").replace("\t", "\\\\t").trim() : "") + "\t");
                            }
                            bufferedWriter.newLine();
                        }
                        bufferedWriter.close();
                        // this may need to be changed, try and find any database with an appropriate import template

                        List<Business> businesses = BusinessDAO.findAll();
                        HashMap<String, ImportTemplateData> templateCache = new HashMap<>();
                        for (Business business : businesses) {
                            List<Database> databases = DatabaseDAO.findForBusinessId(business.getId());
                            for (Database database : databases) {
                                if (database.getImportTemplateId() != 0) {
                                    LoggedInUser loggedInUser = new LoggedInUser(new User(0, LocalDateTime.now(), business.getId(), "tracking", "", "", "", "", "", 0, 0, "", "")
                                            , DatabaseServerDAO.findById(database.getDatabaseServerId()), database, null, business);
                                    ImportTemplateData importTemplateForUploadedFile = ImportService.getImportTemplateForUploadedFile(loggedInUser, null, templateCache);
                                    if (importTemplateForUploadedFile != null && importTemplateForUploadedFile.getSheets().get("ClaimsTracking") != null) { // then here we go . . .
                                        try {
                                            final Map<String, String> fileNameParams = new HashMap<>();
                                            String fileName = tracking.resolve(csvFileName).getFileName().toString();
                                            ImportService.addFileNameParametersToMap(fileName, fileNameParams);
                                            ImportService.importTheFile(loggedInUser, new UploadedFile(tracking.resolve(csvFileName).toString()
                                                    , new ArrayList<>(Collections.singletonList(fileName)), fileNameParams, true, false), null, null);

                                        } catch (Exception e) {
                                            System.out.println("Claims tracking fail for database : " + database.getName());
                                            e.printStackTrace();
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (!premiumsFilesValues.isEmpty()) {
                        String csvFileName = (transNo != null ? transNo : maxKey) + "tracking (importversion=PremiumTracking).tsv";
                        BufferedWriter bufferedWriter = Files.newBufferedWriter(tracking.resolve(csvFileName), StandardCharsets.UTF_8);
                        for (String heading : pheadings) {
                            bufferedWriter.write(heading + "\t");
                        }
                        bufferedWriter.newLine();
                        for (Map<String, StringBuilder> row : premiumsFilesValues) {
                            for (String heading : pheadings) {
                                StringBuilder value = row.get(heading);
                                bufferedWriter.write((value != null ? value.toString().replace("\n", "\\\\n").replace("\t", "\\\\t").trim() : "") + "\t");
                            }
                            bufferedWriter.newLine();
                        }
                        bufferedWriter.close();
                    }
                }
            }
        }
    }
}