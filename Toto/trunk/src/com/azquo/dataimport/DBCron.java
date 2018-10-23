package com.azquo.dataimport;

import com.azquo.admin.AdminService;
import com.azquo.admin.BackupService;
import com.azquo.admin.business.Business;
import com.azquo.admin.business.BusinessDAO;
import com.azquo.admin.database.*;
import com.azquo.memorydb.DatabaseAccessToken;
import com.azquo.rmi.RMIClient;
import com.azquo.spreadsheet.SpreadsheetService;
import org.springframework.scheduling.annotation.Scheduled;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.time.LocalDateTime;
import java.time.ZoneId;
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

    public static final String dbBackupsDirectory = "DBBACKUPS";

    @Scheduled(cron = "0 * * * * *")
    public void demoServiceMethod() {
//        System.out.println("every minute?" + LocalDateTime.now());
    }

    @Scheduled(cron = "0 0 * * * *")
    public void autoDBBakups() throws Exception {
        System.out.println("hourly backup check " + LocalDateTime.now());
        List<Database> forBackup = DatabaseDAO.findForBackup();
        for (Database toBackUp : forBackup){
            // initially put the backup code in here, can factor it off later
//            String backupname = RMIClient.getServerInterface(DatabaseServerDAO.findById(toBackUp.getDatabaseServerId()).getIp()).getMostRecentProvenance(toBackUp.getPersistenceName());
            String backupname = toBackUp.getLastProvenance();
            // make backup name "safer" for file names?
            if (backupname != null && backupname.length() > 0){
                backupname = backupname.replaceAll("[^A-Za-z0-9_]", "");
                /* ok where do the backups go?
                 this is how reports are found String bookPath = SpreadsheetService.getHomeDir() + ImportService.dbPath + loggedInUser.getBusinessDirectory() + ImportService.onlineReportsDir + finalOnlineReport.getFilenameForDisk();
                 this is a directory per business though . . . we want a directory per database really. So make a directory in there! Need to zap on delete . . .
                 */
                String dbBackupsDir = getDBBackupsDirectory(toBackUp);
                if (!Files.exists(Paths.get(dbBackupsDir + "/" + backupname))){ // then we need to make a backup . . .
                    String serverIp = DatabaseServerDAO.findById(toBackUp.getDatabaseServerId()).getIp();
                    BackupService.createDBBackupFile(toBackUp.getName(), new DatabaseAccessToken("","", serverIp, toBackUp.getPersistenceName()), dbBackupsDir + "/" + backupname, serverIp);
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
        synchronized (this){ // one at a time
            if (SpreadsheetService.getScanDir() != null && SpreadsheetService.getScanDir().length() > 0
                    && SpreadsheetService.getScanBusiness() != null && SpreadsheetService.getScanBusiness().length() > 0){
                Business b = BusinessDAO.findByName(SpreadsheetService.getScanBusiness());
                if (b != null){
                    System.out.println("running file scan");
                    // we'll move imported files into loaded when they have been entered into Pending Uploads
                    Path tagged = Paths.get(SpreadsheetService.getScanDir() + "/tagged");
                    if (!Files.exists(tagged)){
                        Files.createDirectories(tagged);
                    }
                    Path p  = Paths.get(SpreadsheetService.getScanDir());
                    Stream<Path> list = Files.list(p);
                    list.forEach(path -> {
                        if (!Files.isDirectory(path)){ // skip any directories
                            String origName = path.getFileName().toString();
                            try {
                                System.out.println("file : " + origName);
                                FileTime lastModifiedTime = Files.getLastModifiedTime(path);
                                Files.move(path, tagged.resolve(System.currentTimeMillis() + origName));
                                // ok it's moved now make the pending upload record
                                PendingUpload pendingUpload = new PendingUpload(0, b.getId()
                                        , LocalDateTime.ofInstant(lastModifiedTime.toInstant(), ZoneId.systemDefault())
                                        , LocalDateTime.now()
                                        , origName
                                        , tagged.resolve(System.currentTimeMillis() + origName).toString()
                                        , "fileimport"
                                ,"waiting"
                                ,""
                                ,0
                                ,0
                                ,null);
                                PendingUploadDAO.store(pendingUpload);
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