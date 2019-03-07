package com.azquo.dataimport;

import com.azquo.admin.BackupService;
import com.azquo.admin.business.Business;
import com.azquo.admin.business.BusinessDAO;
import com.azquo.admin.database.*;
import com.azquo.memorydb.DatabaseAccessToken;
import com.azquo.spreadsheet.SpreadsheetService;
import org.springframework.scheduling.annotation.Scheduled;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
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
        synchronized (this) { // one at a time
            if (SpreadsheetService.getScanBusiness() != null && SpreadsheetService.getScanBusiness().length() > 0) {
                Business b = BusinessDAO.findByName(SpreadsheetService.getScanBusiness());
                if (b != null) {
                    if (SpreadsheetService.getScanDir() != null && SpreadsheetService.getScanDir().length() > 0) {
                        //System.out.println("running file scan");
                        // we'll move imported files into loaded when they have been entered into Pending Uploads
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
                                        FileTime lastModifiedTime = null;
                                        lastModifiedTime = Files.getLastModifiedTime(path);
                                        long timestamp = System.currentTimeMillis();
                                        if (lastModifiedTime.toMillis() < (timestamp - 120_000)) {
                                            System.out.println("file : " + origName);
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
                    // for brokasure, intially just scn the directory like
                    // todo : date (simple), id brokasure adds e.g. CSJan1912.xml-19022815141117-Error the 19022815141117 here and a way to match back to the original XML which probably means timestamping it
                    // matching  back to the original required as a file with an Error just has the error
                    if (SpreadsheetService.getXMLScanDir() != null && SpreadsheetService.getXMLScanDir().length() > 0) {
                        // make tagged as before but this time the plan is to parse all found XML files into a single CSV and upload it
                        Path tagged = Paths.get(SpreadsheetService.getXMLScanDir() + "/tagged");
                        if (!Files.exists(tagged)) {
                            Files.createDirectories(tagged);
                        }
                        Path p = Paths.get(SpreadsheetService.getXMLScanDir());
                        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                        final DocumentBuilder builder = factory.newDocumentBuilder();
                        BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(tagged.resolve(System.currentTimeMillis() + "generatedfromxml.csv").toFile()));
                        //if (lastModifiedTime.toMillis() < (timestamp - 120_000)) {
                        // I'm going to allow for the possiblity that different files might have different fields
                        Set<String> headings = new HashSet<>();
                        List<Map<String, String>> filesValues = new ArrayList<>();
                        try (Stream<Path> list = Files.list(p)) {
                            list.forEach(path -> {
                                // Do stuff
                                if (!Files.isDirectory(path)) { // skip any directories
                                    try {
                                        String origName = path.getFileName().toString();
                                        FileTime lastModifiedTime = null;
                                        lastModifiedTime = Files.getLastModifiedTime(path);
                                        long timestamp = System.currentTimeMillis();
                                        if (lastModifiedTime.toMillis() < (timestamp - 1_000)) {
                                            System.out.println("file : " + origName);
                                            // unlike the above, before moving it I need to read it

                                            try {
                                                Map<String, String> thisFileValues = new HashMap<>();
                                                Document workbookXML = builder.parse(path.toFile());
                                                //workbookXML.getDocumentElement().normalize(); // probably fine on smaller XML, don't want to do on the big stuff
                                                Element documentElement = workbookXML.getDocumentElement();
                                                // this criteria is currently suitable for the simple XML from Brokasure
                                                for (int index = 0; index < documentElement.getChildNodes().getLength(); index++){
                                                    Node node = documentElement.getChildNodes().item(index);
                                                    if (node.hasChildNodes()){
                                                        headings.add(node.getNodeName());
                                                        thisFileValues.put(node.getNodeName(), node.getFirstChild().getNodeValue());
                                                    }
                                                }
                                                filesValues.add(thisFileValues);
                                            } catch (SAXException e) {
                                                e.printStackTrace();
                                            }

                                            Files.move(path, tagged.resolve(timestamp + origName));
                                        } else {
                                            System.out.println("file found for XML but it's only " + ((timestamp - lastModifiedTime.toMillis()) / 1_000) + " seconds old, needs to be 120 seconds old");
                                        }
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    }
                                }
                            });
                        }
                        for (String heading : headings){
                            bufferedWriter.write(heading + "\t");
                        }
                        bufferedWriter.newLine();
                        for (Map<String, String> lineValues : filesValues){
                            for (String heading : headings){
                                String value = lineValues.get(heading);
                                bufferedWriter.write((value != null ? value : "") + "\t");
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