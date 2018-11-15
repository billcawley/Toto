package com.azquo.dataimport;

import com.azquo.TypedPair;
import com.csvreader.CsvWriter;
import com.jcraft.jsch.*;
import org.zkoss.poi.ss.format.CellDateFormatter;
import org.zkoss.poi.ss.usermodel.*;
import org.zkoss.zss.api.Range;
import org.zkoss.zss.api.Ranges;
import org.zkoss.zss.api.model.CellData;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Created by edward on 11/11/16.
 * <p>
 * Factoring off a few functions from ImportService.
 */
class ImportFileUtilities {

    private static SimpleDateFormat YYYYMMDD = new SimpleDateFormat("yyyy-MM-dd");

    // as in drop the import stream into a temp file and return its path
    // todo, better api calls, perhaps making the function redundant
    static File tempFileWithoutDecoding(final InputStream data, final String fileName) throws IOException {
        File temp = File.createTempFile(fileName.substring(0, fileName.length() - 4) + "_", fileName.substring(fileName.length() - 4));
        temp.deleteOnExit();
        FileOutputStream fos = new FileOutputStream(temp);
        org.apache.commons.io.IOUtils.copy(data, fos);
        fos.close();
        return temp;
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

    static void convertRangeToCSV(final Sheet sheet, final CsvWriter csvW) throws Exception {
        int rowIndex = -1;
        for (Row row : sheet) {
            // turns out blank lines are important
            if (++rowIndex != row.getRowNum()) {
                while (rowIndex != row.getRowNum()) {
                    csvW.endRecord();
                    rowIndex++;
                }
            }
            int cellIndex = -1;
            for (Iterator<Cell> ri = row.cellIterator(); ri.hasNext(); ) {
                Cell cell = ri.next();
                if (++cellIndex != cell.getColumnIndex()) {
//                    System.out.println("cell index notu as expectec, found " + cell.getColumnIndex() + ", expected " + cellIndex);
                    while (cellIndex != cell.getColumnIndex()) {
                        csvW.write("");
                        cellIndex++;
                    }
                }
//                System.out.println("cell col : " + cell.getColumnIndex());
                final TypedPair<Double, String> cellValue = getCellValue(cell);
                csvW.write(cellValue.getSecond().replace("\n", "\\\\n").replace("\r", "")
                        .replace("\t", "\\\\t"));
            }
            csvW.endRecord();
        }
    }

    private static DataFormatter df = new DataFormatter();
    // EFC note : I'm not completely happy with this function, I'd like to rewrite. TODO - factor common code

    private static TypedPair<Double, String> getCellValue(Cell cell) {
        Double returnNumber = null;
        String returnString = "";
        //if (colCount++ > 0) bw.write('\t');
        if (cell.getCellType() == Cell.CELL_TYPE_STRING || (cell.getCellType() == Cell.CELL_TYPE_FORMULA && cell.getCachedFormulaResultType() == Cell.CELL_TYPE_STRING)) {
            try {
                returnString = cell.getStringCellValue();// I assume means formatted text?
            } catch (Exception ignored) {
            }
        } else if (cell.getCellType() == Cell.CELL_TYPE_NUMERIC || (cell.getCellType() == Cell.CELL_TYPE_FORMULA && cell.getCachedFormulaResultType() == Cell.CELL_TYPE_NUMERIC)) {
            // first we try to get it without locale - better match on built in formats it seems
            String dataFormat = BuiltinFormats.getBuiltinFormat(cell.getCellStyle().getDataFormat());
            if (dataFormat == null){
                dataFormat = cell.getCellStyle().getDataFormatString();
            }
            returnNumber = cell.getNumericCellValue();
            returnString = returnNumber.toString();
            if (returnString.contains("E")) {
                returnString = String.format("%f", returnNumber);
            }
            if (returnNumber%1 == 0) {
                // specific condition - integer and format all 000, then actually use the format. For zip codes
                if (dataFormat.contains("0") && dataFormat.replace("0","").isEmpty()){
                    returnString = df.formatCellValue(cell);
                } else {
                    returnString = returnNumber.longValue() + "";
                }
            }
            if (dataFormat.equals("h:mm") && returnString.length() == 4) {
                //ZK BUG - reads "hh:mm" as "h:mm"
                returnString = "0" + returnString;
            } else {
                if (dataFormat.toLowerCase().contains("m")) {
                    if (dataFormat.length() > 6){
                        try {
                            returnString = YYYYMMDD.format(cell.getDateCellValue());
                        } catch (Exception e) {
                            //not sure what to do here.
                        }
                    } else { // it's still a date - match the defauilt format
                        // this seems to be required as if the date is based off another cell then the normal formatter will return the formula
                        CellDateFormatter cdf = new CellDateFormatter(dataFormat, Locale.UK);
                        returnString = cdf.format(cell.getDateCellValue());
                    }
                }
            }
            /* I thnk this is redundant due to my call wihtout the locale above
            if ((returnString.length() == 6 || returnString.length() == 8) && returnString.charAt(3) == ' ' && dataFormat.toLowerCase().contains("mm-")) {//another ZK bug
                returnString = returnString.replace(" ", "-");//crude replacement of spaces in dates with dashes
            }*/
/*            if (!dataFormat.equalsIgnoreCase("general")){
                System.out.println("data format, " + dataFormat + " new sting value " + newStringValue);
            }*/
        } else if (cell.getCellType() == Cell.CELL_TYPE_BOOLEAN || (cell.getCellType() == Cell.CELL_TYPE_FORMULA && cell.getCachedFormulaResultType() == Cell.CELL_TYPE_BOOLEAN)) {
            returnString = cell.getBooleanCellValue() + "";
        } else if (cell.getCellType() != Cell.CELL_TYPE_BLANK) {
            if (cell.getCellType() == Cell.CELL_TYPE_FORMULA) {
                System.out.println("other forumla cell type : " + cell.getCachedFormulaResultType());
            }
            System.out.println("other cell type : " + cell.getCellType());
        }
        if (returnString.contains("\"\"") && returnString.startsWith("\"") && returnString.endsWith("\"")) {
            //remove spurious quote marks
            returnString = returnString.substring(1, returnString.length() - 1).replace("\"\"", "\"");
        }
        if (returnString.startsWith("`") && returnString.indexOf("`", 1) < 0) {
            returnString = returnString.substring(1);
        }
        if (returnString.startsWith("'") && returnString.indexOf("'", 1) < 0)
            returnString = returnString.substring(1);//in Excel some cells are preceded by a ' to indicate that they should be handled as strings
        return new TypedPair<>(returnNumber, returnString.trim());
    }

    // EFC note : I'm not completely happy with this function, I'd like to rewrite. TODO

    static TypedPair<Double, String> getCellValue(org.zkoss.zss.api.model.Sheet sheet, int r, int c) {
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
                if (dataFormat.equals("h:mm") && stringValue.length() == 4) {
                    //ZK BUG - reads "hh:mm" as "h:mm"
                    stringValue = "0" + stringValue;
                } else {
                    if (dataFormat.toLowerCase().contains("m") && dataFormat.length() > 6) {
                        try {
                            Date javaDate = DateUtil.getJavaDate((cellData.getDoubleValue()));
                            stringValue = YYYYMMDD.format(javaDate);
                        } catch (Exception e) {
                            //not sure what to do here.
                        }
                    }
                }
                if ((stringValue.length() == 6 || stringValue.length() == 8) && stringValue.charAt(3) == ' ' && dataFormat.toLowerCase().contains("mm-")) {//another ZK bug
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
                        newStringValue = String.format("%f", d);
                    }
                    if (newStringValue.endsWith(".0")) {
                        stringValue = newStringValue.substring(0, newStringValue.length() - 2);
                    } else {
                        if (!newStringValue.endsWith(".000000")) {
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
        assert returnString != null;
        if (returnString.startsWith("`") && returnString.indexOf("'", 1) < 0) {
            returnString = returnString.substring(1);
        }
        if (returnString.startsWith("'") && returnString.indexOf("'", 1) < 0)
            returnString = returnString.substring(1);//in Excel some cells are preceded by a ' to indicate that they should be handled as strings
        return new TypedPair<>(returnNumber, returnString.trim());
    }
}