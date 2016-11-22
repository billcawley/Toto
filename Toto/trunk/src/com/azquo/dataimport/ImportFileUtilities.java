package com.azquo.dataimport;

import com.azquo.TypedPair;
import com.csvreader.CsvWriter;
import com.jcraft.jsch.*;
import org.zkoss.zss.api.Range;
import org.zkoss.zss.api.Ranges;
import org.zkoss.zss.api.model.CellData;
import org.zkoss.zss.api.model.Sheet;
import org.zkoss.zss.model.CellRegion;
import org.zkoss.zss.model.SRow;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Created by edward on 11/11/16.
 *
 * Factoring off a few functions from ImportService.
 */
class ImportFileUtilities {

    static List<File> unZip(String zipFile) {
        String outputFolder;
        if (!zipFile.contains("/")) {
            outputFolder = zipFile.substring(0, zipFile.lastIndexOf("\\"));// same dir
        } else { // normal
            outputFolder = zipFile.substring(0, zipFile.lastIndexOf("/"));// same dir
        }
        byte[] buffer = new byte[1024];
        List<File> toReturn = new ArrayList<>();
        try {
            //create output directory is not exists
            File folder = new File(outputFolder);
            if (!folder.exists()) {
                folder.mkdir();
            }
            //get the zip file content
            ZipInputStream zis =
                    new ZipInputStream(new FileInputStream(zipFile));
            //get the zipped file list entry
            ZipEntry ze = zis.getNextEntry();
            while (ze != null) {
                String fileName = ze.getName();
                File newFile = new File(outputFolder + File.separator + fileName);
                System.out.println("file unzip : " + newFile.getAbsoluteFile());
                //create all non exists folders
                //else you will hit FileNotFoundException for compressed folder
                if (ze.isDirectory()) {
                    newFile.mkdirs();
                } else {
                    toReturn.add(newFile);
                    FileOutputStream fos = new FileOutputStream(newFile);
                    int len;
                    while ((len = zis.read(buffer)) > 0) {
                        fos.write(buffer, 0, len);
                    }
                    fos.close();
                }
                ze = zis.getNextEntry();
            }
            zis.closeEntry();
            zis.close();
            System.out.println("Done");
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        return toReturn;
    }

    // as in drop the import stream into a temp file and return its path

    static String tempFileWithoutDecoding(final InputStream data, final String fileName) {
        try {
            File temp = File.createTempFile(fileName.substring(0, fileName.length() - 4) + "_", fileName.substring(fileName.length() - 4));
            String tempFile = temp.getPath();
            temp.deleteOnExit();
            FileOutputStream fos = new FileOutputStream(tempFile);
            org.apache.commons.io.IOUtils.copy(data, fos);
            fos.close();
            return tempFile;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }

    static String copyFileToDatabaseServer(InputStream inputStream, String sftpDestination) {
        /*
        StandardFileSystemManager manager = new StandardFileSystemManager();
        String toReturn = null;
        try {
            File file = new File(filePath);
            if (!file.exists()) {
                throw new RuntimeException("Error. Local file not found");
            }
            //Initializes the file manager
            manager.init();
            //Setup our SFTP configuration
            FileSystemOptions opts = new FileSystemOptions();
            SftpFileSystemConfigBuilder.getInstance().setStrictHostKeyChecking(opts, "no");
            SftpFileSystemConfigBuilder.getInstance().setUserDirIsRoot(opts, false);
            SftpFileSystemConfigBuilder.getInstance().setTimeout(opts, 10000);
            String fileName = filePath.substring(filePath.lastIndexOf("/") + 1);
            //Create the SFTP URI using the host name, userid, password,  remote path and file name
            String sftpUri = sftpDestination + fileName;
            // Create local file object
            FileObject localFile = manager.resolveFile(file.getAbsolutePath());
            // Create remote file object
            FileObject remoteFile = manager.resolveFile(sftpUri, opts);
            // Copy local file to sftp server
            remoteFile.copyFrom(localFile, Selectors.SELECT_SELF);
            System.out.println("File upload successful");
            int atPoint = sftpDestination.indexOf("@");
            toReturn = sftpDestination.substring(sftpDestination.indexOf("/", atPoint)) + fileName; // should be the path of the file on the remote machine.
            // I'm going to zap the file now
            localFile.delete();
        } catch (Exception ex) {
            ex.printStackTrace();
            return null;
        } finally {
            manager.close();
        }
        return toReturn;
    }

    public  void send (String fileName) {
    */

        int userPos = sftpDestination.indexOf("//") + 2;
        int passPos = sftpDestination.indexOf(".", userPos);
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
        System.out.println("preparing the host information for sftp.");
        try {
            JSch jsch = new JSch();
            session = jsch.getSession(SFTPUSER, SFTPHOST, SFTPPORT);
            session.setPassword(SFTPPASS);
            java.util.Properties config = new java.util.Properties();
            config.put("StrictHostKeyChecking", "no");
            session.setConfig(config);
            session.connect();
            System.out.println("Host connected.");
            channel = session.openChannel("sftp");
            channel.connect();
            System.out.println("sftp channel opened and connected.");
            channelSftp = (ChannelSftp) channel;
            sftpCd(channelSftp, SFTPWORKINGDIR);
            channelSftp.put(inputStream, fileName);
            //log.info("File transfered successfully to host.");
        } catch (Exception ex) {
            System.out.println("Exception found while tranfer the response.");
        } finally {
            if (channelSftp != null) {
                channelSftp.exit();
                System.out.println("sftp Channel exited.");
                channel.disconnect();
                System.out.println("Channel disconnected.");
                session.disconnect();
                System.out.println("Host Session disconnected.");
            }
        }
        return "file copied successfully";
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

    static void convertRangeToCSV(final Sheet sheet, final CsvWriter csvW, CellRegion range, Map<String, String> newNames, boolean transpose) throws Exception {

        /*  NewNames here is a very short list which will convert 'next...' into the next available number for that variable, the value being held as the attribute 'next' on that name - e.g. for invoices
        *   the code is now defunct, but was present at SVN version 1161
        *
        * */
        int rows = sheet.getLastRow();
        int maxCol = 0;
        for (int row = 0; row <= sheet.getLastRow(); row++) {
            if (maxCol < sheet.getLastColumn(row)) {
                maxCol = sheet.getLastColumn(row);
            }
        }
        int startRow = 0;
        int startCol = 0;
        if (range != null) {
            startRow = range.getRow();
            startCol = range.getColumn();
            rows = startRow + range.getRowCount() - 1;
            maxCol = startCol + range.getColumnCount() - 1;
        }

        if (!transpose) {
            for (int r = startRow; r <= rows; r++) {
                SRow row = sheet.getInternalSheet().getRow(r);
                if (row != null) {
                    //System.out.println("Excel row " + r);
                    //int colCount = 0;
                    for (int c = startCol; c <= maxCol; c++) {
                        writeCell(sheet, r, c, csvW, newNames);
                    }
                    csvW.endRecord();
                }
            }
        } else {
            for (int c = startCol; c <= maxCol; c++) {
                for (int r = startRow; r <= rows; r++) {
                    writeCell(sheet, r, c, csvW, newNames);
                }
                csvW.endRecord();
            }
        }
    }

    private static void writeCell(Sheet sheet, int r, int c, CsvWriter csvW, Map<String, String> newNames) throws Exception {
        final TypedPair<Double, String> cellValue = getCellValue(sheet, r, c);
        if (newNames != null && newNames.get(cellValue.getSecond()) != null) {
            csvW.write(newNames.get(cellValue.getSecond()));
        } else {
            csvW.write(cellValue.getSecond().replace("\n", "\\\\n").replace("\t", "\\\\t"));//nullify the tabs and carriage returns.  Note that the double slash is deliberate so as not to confuse inserted \\n with existing \n
        }

    }

    // EFC note : I'm not completely happy with this function, I'd like to rewrite. TODO

    static TypedPair<Double, String> getCellValue(Sheet sheet, int r, int c) {
        Double returnNumber = null;
        String returnString = null;
        Range range = Ranges.range(sheet, r, c);
        CellData cellData = range.getCellData();
        String dataFormat = sheet.getInternalSheet().getCell(r, c).getCellStyle().getDataFormat();
        //if (colCount++ > 0) bw.write('\t');
        if (cellData != null) {
            String stringValue = "";
            try {
                stringValue = cellData.getFormatText();// I assume means formatted text
                if (r > 0 && dataFormat.toLowerCase().contains("mm-")) {//fix a ZK bug
                    stringValue = stringValue.replace(" ", "-");//crude replacement of spaces in dates with dashes
                }
            } catch (Exception ignored) {
            }
            if (!dataFormat.toLowerCase().contains("m")) {//check that it is not a date or a time
                //if it's a number, remove all formatting
                try {
                    double d = cellData.getDoubleValue();
                    returnNumber = d;
                    stringValue = d + "";
                    if (stringValue.endsWith(".0")) {
                        stringValue = stringValue.substring(0, stringValue.length() - 2);
                    }
                } catch (Exception ignored) {
                }
            }
            if (stringValue.contains("\"\"") && stringValue.startsWith("\"") && stringValue.endsWith("\"")) {
                //remove spuriouse quote marks
                stringValue = stringValue.substring(1, stringValue.length() - 1).replace("\"\"", "\"");
            }
            returnString = stringValue;
        }
        return new TypedPair<>(returnNumber, returnString);
    }
}