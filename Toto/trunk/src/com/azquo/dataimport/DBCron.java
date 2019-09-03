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
import org.springframework.scheduling.annotation.Scheduled;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/**
 * Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
 * <p>
 * Created by cawley on 20/04/15.
 * <p>
 * Currently unused but it may be useful so I'm leaving it here
 */
public class DBCron {

    private static final String dbBackupsDirectory = "DBBACKUPS";

    @Scheduled(cron = "0 * * * * *")
    public void demoServiceMethod() {
//        System.out.println("every minute?" + LocalDateTime.now());
    }

    @Scheduled(cron = "0 0 * * * *")
    public void autoDBBakups() throws Exception {
        System.out.println("hourly backup check " + LocalDateTime.now());
        List<Database> forBackup = DatabaseDAO.findForAutoBackup();
        for (Database toBackUp : forBackup) {
            // initially put the backup code in here, can factor it off later
//            String backupname = RMIClient.getServerInterface(DatabaseServerDAO.findpById(toBackUp.getDatabaseServerId()).getIp()).getMostRecentProvenance(toBackUp.getPersistenceName());
            String backupname = toBackUp.getLastProvenance();
            // make backup name "safer" for file names?
            if (backupname != null && backupname.length() > 0) {
                backupname = backupname.replaceAll("[^A-Za-z0-9_]", "");
                /* ok where do the backups go?
                 this is how reports are found String bookPath = SpreadsheetService.getHomeDir() + ImportService.dbPath + loggedInUser.getBusinessDirectory() + ImportService.onlineReportsDir + finalOnlineReport.getFilenameForDisk();
                 this is a directory per business though . . . we want a directory per database really. So make a directory in there! Need to zap on delete . . .
                 */
                String dbBackupsDir = getDBBackupsDirectory(toBackUp);
                if (!Files.exists(Paths.get(dbBackupsDir + "/" + backupname))) { // then we need to make a backup . . .
                    String serverIp = DatabaseServerDAO.findById(toBackUp.getDatabaseServerId()).getIp();
                    BackupService.createDBBackupFile(toBackUp.getName(), new DatabaseAccessToken("", "", serverIp, toBackUp.getPersistenceName()), dbBackupsDir + "/" + backupname, serverIp);
                }
            }
        }
    }

    public static String getDBBackupsDirectory(Database toBackUp) throws IOException {
        Business b = BusinessDAO.findById(toBackUp.getBusinessId());
        String businessDir = SpreadsheetService.getHomeDir() + ImportService.dbPath + b.getBusinessDirectory();
        String dbBackupsDir = businessDir + "/" + dbBackupsDirectory + toBackUp.getPersistenceName(); // persistence name less likely to cause a problem with the file system
        Files.createDirectories(Paths.get(dbBackupsDir));
        return dbBackupsDir;
    }

    // need to think of how to define this
    @Scheduled(cron = "0 * * * * *")
    public void directoryScan() throws Exception {
        final long millisOldThreshold = 300_000;
        synchronized (this) { // one at a time
            if (SpreadsheetService.getScanBusiness() != null && SpreadsheetService.getScanBusiness().length() > 0) {
                Business b = BusinessDAO.findByName(SpreadsheetService.getScanBusiness());
                if (b != null) {
                    if (SpreadsheetService.getScanDir() != null && SpreadsheetService.getScanDir().length() > 0) {
                        //System.out.println("running file scan");
                        // todo - move tagged out of here if the scan dir is a mapped drive as it may become
                        Path tagged = Paths.get(SpreadsheetService.getScanDir() + "/tagged");
                        if (!Files.exists(tagged)) {
                            Files.createDirectories(tagged);
                        }

                        Path p = Paths.get(SpreadsheetService.getScanDir());
                        try (Stream<Path> list = Files.list(p)) {
                            list.forEach(path -> {
                                // Do stuff
                                if (!Files.isDirectory(path)) { // skip any directories
                                    try {
                                        String origName = path.getFileName().toString();
                                        FileTime lastModifiedTime;
                                        lastModifiedTime = Files.getLastModifiedTime(path);
                                        long timestamp = System.currentTimeMillis();
                                        if (lastModifiedTime.toMillis() < (timestamp - 120_000)) {
                                            Files.move(path, tagged.resolve(timestamp + origName));
                                            // ok it's moved now make the pending upload record
                                            // todo - assign the database and team automatically!
                                            PendingUpload pendingUpload = new PendingUpload(0, b.getId()
                                                    , LocalDateTime.ofInstant(lastModifiedTime.toInstant(), ZoneId.systemDefault())
                                                    , null
                                                    , origName
                                                    , tagged.resolve(timestamp + origName).toString()
                                                    , -1
                                                    , -1
                                                    , 1
                                                    , null, null);
                                            PendingUploadDAO.store(pendingUpload);
                                        } else {
                                            System.out.println("file found for pending but it's only " + ((timestamp - lastModifiedTime.toMillis()) / 1_000) + " seconds old, needs to be 120 seconds old");
                                        }
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    }
                                }
                            });
                        }
                    }

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
                                                        Path leftoverXLSX = Paths.get(SpreadsheetService.getXMLDestinationDir()).resolve(fileKey + ".xlsx");
                                                        if (Files.exists(leftoverXLSX)) {
                                                            Files.delete(leftoverXLSX);
                                                        } else if (fileKey.toLowerCase().startsWith("cs") && !error) { // it was zapped (as in ok!) - in the case of CS claim settlements the original file which will be in temp now needs to go in the outbox - if there was no error of course!
                                                            Path xlsxFileToMove = Paths.get(SpreadsheetService.getHomeDir() + "/temp/" + fileKey + ".xlsx");
                                                            if (Files.exists(xlsxFileToMove)) {
                                                                System.out.println("moving file back to the out box " + fileKey + ".xlsx");
                                                                Files.move(xlsxFileToMove, Paths.get(SpreadsheetService.getXMLScanDir()).resolve(fileKey + ".xlsx"));
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
                                String csvFileName = fileMillis + "generatedfromxml (importtemplate=BrokasureTemplates;importversion=Brokasure" + rootDocumentName.get() + ").tsv";
                                BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(tagged.resolve(csvFileName).toFile()));
                                for (String heading : headings) {
                                    bufferedWriter.write(heading + "\t");
                                }
                                bufferedWriter.newLine();
                                for (Map<String, String> lineValues : filesValues.values()) {
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
                                if (Files.exists(newScannedDir.resolve(csvFileName)) && SpreadsheetService.getXMLScanDB() != null && !SpreadsheetService.getXMLScanDB().isEmpty()) { // it should exist and a database should be set also
                                    // hacky, need to sort, todo
                                    Database db = DatabaseDAO.findForNameAndBusinessId(SpreadsheetService.getXMLScanDB(), b.getId());
                                    LoggedInUser loggedInUser = new LoggedInUser(""
                                            , new User(0, LocalDateTime.now(), b.getId(), "brokasure", "", "", "", "", "", 0, 0, "", "")
                                            , DatabaseServerDAO.findById(db.getDatabaseServerId()), db, null, b.getBusinessDirectory());
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
                                                    LoggedInUser loggedInUser = new LoggedInUser(""
                                                            , new User(0, LocalDateTime.now(), b.getId(), "brokasure", "", "", "", "", "", 0, 0, "", "")
                                                            , DatabaseServerDAO.findById(matchingDBdir.getDatabaseServerId()), matchingDBdir, null, b.getBusinessDirectory());
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
    private static boolean readXML(String fileKey, Map<String, Map<String, String>> filesValues, AtomicReference<String> rootDocumentName
            , DocumentBuilder builder, Path path, Set<String> headings, FileTime lastModifiedTime) throws IOException, SAXException {
        // unlike the above, before moving it I need to read it
        Map<String, String> thisFileValues = filesValues.computeIfAbsent(fileKey, t -> new HashMap<>());
        Document workbookXML = builder.parse(path.toFile());
        //workbookXML.getDocumentElement().normalize(); // probably fine on smaller XML, don't want to do on the big stuff
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

    @Scheduled(cron = "0 * * * * *")
    public void runScheduledReports() throws Exception {
        synchronized (this) { // one at a time
            SpreadsheetService.runScheduledReports();
        }
    }
}