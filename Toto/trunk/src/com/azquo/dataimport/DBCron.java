package com.azquo.dataimport;

import com.azquo.admin.AdminService;
import com.azquo.admin.BackupService;
import com.azquo.admin.business.Business;
import com.azquo.admin.business.BusinessDAO;
import com.azquo.admin.database.Database;
import com.azquo.admin.database.DatabaseDAO;
import com.azquo.admin.database.DatabaseServerDAO;
import com.azquo.memorydb.DatabaseAccessToken;
import com.azquo.rmi.RMIClient;
import com.azquo.spreadsheet.SpreadsheetService;
import org.springframework.scheduling.annotation.Scheduled;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;

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
        // todo - factor, this could cause problems!
        Business b = BusinessDAO.findById(toBackUp.getBusinessId());
        String businessDirectory = (b.getBusinessName() + "                    ").substring(0, 20).trim().replaceAll("[^A-Za-z0-9_]", "");
        String businessDir = SpreadsheetService.getHomeDir() + ImportService.dbPath + businessDirectory;
        String dbBackupsDir = businessDir + "/" + dbBackupsDirectory + toBackUp.getPersistenceName(); // persistence name less likely to cause a problem with the file system
        Files.createDirectories(Paths.get(dbBackupsDir));
        return dbBackupsDir;
    }
}