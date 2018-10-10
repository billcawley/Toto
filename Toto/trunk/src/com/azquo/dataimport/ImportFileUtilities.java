package com.azquo.dataimport;

import com.azquo.TypedPair;
import com.csvreader.CsvWriter;
import com.jcraft.jsch.*;
import org.zkoss.zss.api.Range;
import org.zkoss.zss.api.Ranges;
import org.zkoss.zss.api.model.CellData;
import org.zkoss.zss.api.model.Sheet;
import org.zkoss.zss.model.SRow;

import java.io.*;

/**
 * Created by edward on 11/11/16.
 * <p>
 * Factoring off a few functions from ImportService.
 *
 */
class ImportFileUtilities {

    // as in drop the import stream into a temp file and return its path
    // todo, better api calls, perhaps making the function redundant
    static File tempFileWithoutDecoding(final InputStream data, final String fileName) {
        try {
            File temp = File.createTempFile(fileName.substring(0, fileName.length() - 4) + "_", fileName.substring(fileName.length() - 4));
            temp.deleteOnExit();
            FileOutputStream fos = new FileOutputStream(temp);
            org.apache.commons.io.IOUtils.copy(data, fos);
            fos.close();
            return temp;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
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

    static void convertRangeToCSV(final Sheet sheet, final CsvWriter csvW, boolean transpose) throws Exception {
        int rows = sheet.getLastRow();
        int maxCol = 0;
        for (int row = 0; row <= sheet.getLastRow(); row++) {
            if (maxCol < sheet.getLastColumn(row)) {
                maxCol = sheet.getLastColumn(row);
            }
        }
        int startRow = 0;
        int startCol = 0;
/*        if (range != null) {
            startRow = range.getRow();
            startCol = range.getColumn();
            rows = startRow + range.getRowCount() - 1;
            maxCol = startCol + range.getColumnCount() - 1;
        }*/

        if (!transpose) {
            for (int r = startRow; r <= rows; r++) {
                SRow row = sheet.getInternalSheet().getRow(r);
                if (row != null) {
                    //System.out.println("Excel row " + r);
                    //int colCount = 0;
                    for (int c = startCol; c <= maxCol; c++) {
                        writeCell(sheet, r, c, csvW);
                    }
                    csvW.endRecord();
                }
            }
        } else {
            for (int c = startCol; c <= maxCol; c++) {
                for (int r = startRow; r <= rows; r++) {
                    writeCell(sheet, r, c, csvW);
                }
                csvW.endRecord();
            }
        }
    }

    private static void writeCell(Sheet sheet, int r, int c, CsvWriter csvW) throws Exception {
        final TypedPair<Double, String> cellValue = getCellValue(sheet, r, c);
        csvW.write(cellValue.getSecond().replace("\n", "\\\\n").replace("\r", "")
                .replace("\t", "\\\\t"));
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
                if (dataFormat.equals("h:mm") && stringValue.length()==4) {
                    //ZK BUG - reads "hh:mm" as "h:mm"
                    stringValue = "0"+stringValue;
                }
                if ((stringValue.length() == 6 || stringValue.length()==8) && stringValue.charAt(3) == ' ' && dataFormat.toLowerCase().contains("mm-") ) {//another ZK bug
                    stringValue = stringValue.replace(" ", "-");//crude replacement of spaces in dates with dashes
                }
            } catch (Exception ignored) {
            }
            if (stringValue.endsWith("%") || (stringValue.contains(".") || !stringValue.startsWith("0")) && !dataFormat.toLowerCase().contains("m")) {//check that it is not a date or a time
                //if it's a number, remove all formatting
               try {
                    double d = cellData.getDoubleValue();
                    returnNumber = d;
                    String newStringValue = d + "";
                    if (newStringValue.contains("E")) {
                        newStringValue = String.format("%f",d);

                    }
                    if (newStringValue.endsWith(".0")) {
                        stringValue = newStringValue.substring(0, newStringValue.length() - 2);
                    } else {
                        if (!newStringValue.endsWith(".000000")){
                            stringValue = newStringValue;
                        }
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
        if (returnString.startsWith("'") && returnString.indexOf("'",1) <0) returnString = returnString.substring(1);//in Excel some cells are preceded by a ' to indicate that they should be handled as strings
        return new TypedPair<>(returnNumber, returnString);
    }
}