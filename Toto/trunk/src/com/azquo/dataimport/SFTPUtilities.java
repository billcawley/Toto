package com.azquo.dataimport;

import com.jcraft.jsch.*;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
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

    public static File copyFromDatabaseServer(String sftpSource, boolean delete) throws IOException {
        int userPos = sftpSource.indexOf("//") + 2;
        int passPos = sftpSource.indexOf(":", userPos);
        int passEnd = sftpSource.indexOf("@", passPos);
        int pathPos = sftpSource.indexOf("/", passEnd);
        int pathEnd = sftpSource.lastIndexOf("/");

        String SFTPHOST = sftpSource.substring(passEnd + 1, pathPos);
        int SFTPPORT = 22;
        String SFTPUSER = sftpSource.substring(userPos, passPos);
        String SFTPPASS = sftpSource.substring(passPos + 1, passEnd);
        String SFTPWORKINGDIR = sftpSource.substring(pathPos, pathEnd);
        String fileName = SFTPWORKINGDIR + "/" + sftpSource.substring(pathEnd + 1);

        Session session = null;
        Channel channel = null;
        File tempFile = File.createTempFile("holding", ".db");
        ChannelSftp channelSftp = null;
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
            System.out.println("sftp get file name : " + fileName);
            InputStream inputStream = channelSftp.get(fileName);
            FileUtils.copyInputStreamToFile(inputStream, tempFile);
            if (delete){
                channelSftp.rm(fileName);
            }
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
        return tempFile;
    }


}