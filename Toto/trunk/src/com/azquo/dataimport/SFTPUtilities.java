package com.azquo.dataimport;

import com.jcraft.jsch.*;

import java.io.InputStream;

/**
 * Created by edward on 11/11/16.
 * <p>
 * Just some basic sftp functions.
 */
public class SFTPUtilities {


    public static String copyFileToDatabaseServer(InputStream inputStream, String sftpDestination) {
        int userPos = sftpDestination.indexOf("//") + 2;
        int passPos = sftpDestination.indexOf(":", userPos);
        int passEnd = sftpDestination.indexOf("@", passPos);
        int pathPos = sftpDestination.indexOf("/", passEnd);
        int pathEnd = sftpDestination.lastIndexOf("/");

        String SFTPHOST = sftpDestination.substring(passEnd + 1, pathPos);
        int SFTPPORT = 22;
        String SFTPUSER = sftpDestination.substring(userPos, passPos);
        String SFTPPASS = sftpDestination.substring(passPos + 1, passEnd);
        String SFTPWORKINGDIR = sftpDestination.substring(pathPos, pathEnd);
        String fileName = sftpDestination.substring(pathEnd + 1);

        Session session = null;
        Channel channel = null;
        ChannelSftp channelSftp = null;

/*
        template path : /home/azquo/databases/JoeBrowns/importtemplates/512-JB Import Templates 2020-10-20b.xlsx
        4: java.lang.StringIndexOutOfBoundsException: String index out of range: 0
        at com.jcraft.jsch.ChannelSftp.put(ChannelSftp.java:551)
        at com.jcraft.jsch.ChannelSftp.put(ChannelSftp.java:492)
        at com.azquo.dataimport.SFTPUtilities.copyFileToDatabaseServer(SFTPUtilities.java:48)
        at com.azquo.dataimport.ImportService.readPreparedFile(ImportService.java:1157)
        at com.azquo.dataimport.ImportService.checkForCompressionAndImport(ImportService.java:344)
        at com.azquo.dataimport.ImportService.importTheFile(ImportService.java:163)
        at com.azquo.dataimport.ImportService.importTheFile(ImportService.java:110)
        at com.azquo.dataimport.DBCron.directoryScan(DBCron.java:298)
        at jdk.internal.reflect.GeneratedMethodAccessor85.invoke(Unknown Source)
        at java.base/jdk.internal.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
        at java.base/java.lang.reflect.Method.invoke(Method.java:567)
        at org.springframework.scheduling.support.ScheduledMethodRunnable.run(ScheduledMethodRunnable.java:65)
        at org.springframework.scheduling.support.DelegatingErrorHandlingRunnable.run(DelegatingErrorHandlingRunnable.java:54)
        at org.springframework.scheduling.concurrent.ReschedulingRunnable.run(ReschedulingRunnable.java:81)
        at java.base/java.util.concurrent.Executors$RunnableAdapter.call(Executors.java:515)
        at java.base/java.util.concurrent.FutureTask.run(FutureTask.java:264)
        at java.base/java.util.concurrent.ScheduledThreadPoolExecutor$ScheduledFutureTask.run(ScheduledThreadPoolExecutor.java:304)
        at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1128)
        at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:628)
        at java.base/java.lang.Thread.run(Thread.java:830)
        Caused by: java.lang.StringIndexOutOfBoundsException: String index out of range: 0
        at java.base/java.lang.StringLatin1.charAt(StringLatin1.java:48)
        at java.base/java.lang.String.charAt(String.java:709)
        at com.jcraft.jsch.ChannelSftp.remoteAbsolutePath(ChannelSftp.java:2903)
        at com.jcraft.jsch.ChannelSftp.put(ChannelSftp.java:517)
        ... 19 more
  */

//        System.out.println("preparing the host information for sftp.");
        String destinationFile = "copied file not set";
        try {
            JSch jsch = new JSch();
            session = jsch.getSession(SFTPUSER, SFTPHOST, SFTPPORT);
            session.setPassword(SFTPPASS);
            java.util.Properties config = new java.util.Properties();
            config.put("StrictHostKeyChecking", "no");
            session.setConfig(config);
            session.connect();
//            System.out.println("Host connected.");
            channel = session.openChannel("sftp");
            channel.connect();
//            System.out.println("sftp channel opened and connected.");
            channelSftp = (ChannelSftp) channel;
            sftpCd(channelSftp, SFTPWORKINGDIR);
            System.out.println("sftp put file name : " + fileName);
            channelSftp.put(inputStream, fileName);
            destinationFile = channelSftp.pwd() + "/" + fileName;
            //log.info("File transferred successfully to host.");
        } catch (Exception ex) {
            ex.printStackTrace();
        } finally {
            if (channelSftp != null) {
                channelSftp.exit();
//                System.out.println("sftp Channel exited.");
                channel.disconnect();
//                System.out.println("Channel disconnected.");
                session.disconnect();
//                System.out.println("Host Session disconnected.");
            }
        }
        return destinationFile;
    }

    private static void sftpCd(ChannelSftp sftp, String path) throws SftpException {
        String[] folders = path.split("/");
        for (String folder : folders) {
            if (folder.length() > 0) {
                try {
                    sftp.cd(folder);
                } catch (SftpException e) {
                    sftp.mkdir(folder);
                    sftp.cd(folder);
                }
            }
        }
    }
}