package com.azquo.dataimport;

import com.azquo.admin.BackupService;
import com.azquo.admin.business.Business;
import com.azquo.admin.business.BusinessDAO;
import com.azquo.admin.database.*;
import com.azquo.memorydb.DatabaseAccessToken;
import com.azquo.spreadsheet.SpreadsheetService;
import org.springframework.scheduling.annotation.Scheduled;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Iterator;
import java.util.List;
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
            if (SpreadsheetService.getScanDir() != null && SpreadsheetService.getScanDir().length() > 0
                    && SpreadsheetService.getScanBusiness() != null && SpreadsheetService.getScanBusiness().length() > 0) {
                Business b = BusinessDAO.findByName(SpreadsheetService.getScanBusiness());
                if (b != null) {
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
                                        System.out.println("fine found for pending but it's only " + ((timestamp - lastModifiedTime.toMillis()) / 1_000) + " seconds old, needs to be 120 seconds old");
                                    }
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }
                        });
                    }
                }
            }
        }
    }
}