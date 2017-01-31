package com.azquo.spreadsheet.zk;

import com.azquo.TypedPair;
import com.azquo.admin.database.Database;
import com.azquo.admin.onlinereport.OnlineReport;
import com.azquo.spreadsheet.LoggedInUser;
import org.apache.commons.lang.math.NumberUtils;
import org.zkoss.zss.api.Range;
import org.zkoss.zss.api.Ranges;
import org.zkoss.zss.api.model.Book;
import org.zkoss.zss.api.model.CellData;
import org.zkoss.zss.api.model.Sheet;
import org.zkoss.zss.model.CellRegion;
import org.zkoss.zss.model.SCell;
import org.zkoss.zss.model.SName;
import org.zkoss.zss.model.SSheet;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by edward on 09/01/17.
 *
 * Low level ZK manipulation functions.
 */
public class BookUtils {
    static List<List<String>> nameToStringLists(SName sName) {
        List<List<String>> toReturn = new ArrayList<>();
        if (sName == null) return toReturn;
        CellRegion region = sName.getRefersToCellRegion();
        if (region == null) return toReturn;
        SSheet sheet = sName.getBook().getSheetByName(sName.getRefersToSheetName());
        for (int rowIndex = region.getRow(); rowIndex <= region.getLastRow(); rowIndex++) {
            List<String> row = new ArrayList<>();
            toReturn.add(row);
            for (int colIndex = region.getColumn(); colIndex <= region.getLastColumn(); colIndex++) {
                SCell cell = sheet.getCell(rowIndex, colIndex);
                // being paraniod?
                if (cell != null && cell.getType() == SCell.CellType.FORMULA) {
                    //System.out.println("doing the cell thing on " + cell);
                    cell.getFormulaResultType();
                    cell.clearFormulaResultCache();
                }
                // I assume non null cell has a non null string value, this may not be true. Also will I get another type of exception?
                try {
                    row.add(cell.getStringValue());
                } catch (Exception e) {

                    if (cell != null && !cell.getType().equals(SCell.CellType.BLANK)) {
                        try{
                            String numberGuess = cell.getNumberValue() + "";
                            if (numberGuess.endsWith(".0")) {
                                numberGuess = numberGuess.substring(0, numberGuess.length() - 2);
                            }
                            if (numberGuess.equals("0")) numberGuess = "";
                            row.add(numberGuess);
                        }catch(Exception e2){
                            row.add(cell.getStringValue());
                        }
                    }else{
                        row.add("");
                    }
                }
            }
        }
        return toReturn;
    }

    static String getRegionValue(Sheet sheet, CellRegion region) {
        try {
            return sheet.getInternalSheet().getCell(region.getRow(), region.getColumn()).getStringValue();
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    // this works out case insensitive based on the API, Like Excel I think.

    public static CellRegion getCellRegionForSheetAndName(Sheet sheet, String name) {
        SName toReturn = sheet.getBook().getInternalBook().getNameByName(name, sheet.getSheetName());
        try {
            if (toReturn == null) {// often may fail with explicit sheet name
                toReturn = sheet.getBook().getInternalBook().getNameByName(name);
            }
            if (toReturn != null && toReturn.getRefersToSheetName().equals(sheet.getSheetName())) {
                return toReturn.getRefersToCellRegion();
            }
            return null;
        } catch (Exception e) {
            return null;//the name exists, but has a dud pointer to a range
        }
    }

    public static SCell getSnameCell(SName sName) {
        if (sName == null) return null;
        // todo - with a poorly configured sheet this can NPE
        return sName.getBook().getSheetByName(sName.getRefersToSheetName()).getCell(sName.getRefersToCellRegion().getRow(), sName.getRefersToCellRegion().getColumn());
    }

    static String rangeToText(int row, int col) {
        if (col > 26) {
            int hbit = (col - 1) / 26;
            return "$" + (char) (hbit + 64) + (char) (col - hbit * 26 + 64) + "$" + (row + 1);
        }
        return "$" + (char) (col + 65) + "$" + (row + 1);
    }

    // moved/adapted from ZKComposer and made static
    static List<SName> getNamedDataRegionForRowAndColumnSelectedSheet(int row, int col, Sheet sheet) {
        return getNamedRegionForRowAndColumnSelectedSheet(row, col, sheet, ReportRenderer.AZDATAREGION);
    }

    // moved/adapted from ZKComposer and made static
    static List<SName> getNamedRegionForRowAndColumnSelectedSheet(int row, int col, Sheet sheet, String prefix) {
        prefix = prefix.toLowerCase();
        List<SName> found = new ArrayList<>();
        for (SName name : getNamesForSheet(sheet)) { // seems best to loop through names checking which matches I think
            if (name.getName().toLowerCase().startsWith(prefix)
                    && name.getRefersToCellRegion() != null
                    && row >= name.getRefersToCellRegion().getRow() && row <= name.getRefersToCellRegion().getLastRow()
                    && col >= name.getRefersToCellRegion().getColumn() && col <= name.getRefersToCellRegion().getLastColumn()) {
                //check that there are some row headings
                found.add(name);
            }
        }
        return found;
    }

    static void setValue(SCell sCell, String sValue) {
        //when setting Excel cell values we need to check  - in order - for times, dates and numbers in general
        sCell.setStringValue(sValue);
        if (sCell.getCellStyle().getDataFormat().equals("@")) {
            //if the cell is formatted as text, then don't try numbers
            return;
        }
        try {
            int colonPos = sValue.length() - 3;
            if (sValue.charAt(colonPos) == ':') {
                double hour = Double.parseDouble(sValue.substring(0, colonPos));
                double minute = Double.parseDouble(sValue.substring(colonPos + 1));
                sCell.setNumberValue((hour + minute / 60) / 24);
                return;
            }
        } catch (Exception e) {
            //not a time after all
        }
        String format = sCell.getCellStyle().getDataFormat();
        if (format.toLowerCase().contains("m")) {//allow users to format their own dates.  All dates on file as values are yyyy-MM-dd
            LocalDate date = ReportUtils.isADate(sValue);
            if (date != null) {
                sCell.setDateValue(Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant()));
                return;
            }
        }
        if (NumberUtils.isNumber(sValue)) {
            sCell.setValue(Double.parseDouble(sValue));
        }
    }

    static int getMaxCol(Sheet sheet) {
        int maxCol = 0;
        for (int i = 0; i <= sheet.getLastRow(); i++) {
            if (sheet.getLastColumn(i) > maxCol) {
                maxCol = sheet.getLastColumn(i);
            }
        }
        return maxCol;
    }

    public static List<SName> getNamesForSheet(Sheet sheet) {
        List<SName> names = new ArrayList<>();
        for (SName name : sheet.getBook().getInternalBook().getNames()) {
            if (name.getRefersToSheetName() != null && name.getRefersToSheetName().equals(sheet.getSheetName())) {
                names.add(name);
            }
        }
        names.sort((o1, o2) -> (o1.getName().toUpperCase().compareTo(o2.getName().toUpperCase())));
        return names;
    }

    static String getCellString(Sheet sheet, int r, int c) {//this is the same routine as in ImportService, so one is redundant, but I'm not sure which (WFC)
        Range range = Ranges.range(sheet, r, c);
        CellData cellData = range.getCellData();
        String dataFormat = sheet.getInternalSheet().getCell(r, c).getCellStyle().getDataFormat();
        //if (colCount++ > 0) bw.write('\t');
        if (cellData != null) {
            String cellFormat = "";
            try {
                cellFormat = cellData.getFormatText();
                if (dataFormat.toLowerCase().contains("mm-")) {//fix a ZK bug
                    cellFormat = cellFormat.replace(" ", "-");//crude replacement of spaces in dates with dashes
                }
            } catch (Exception ignored) {
            }
            if (!dataFormat.toLowerCase().contains("m")) {//check that it is not a data or a time
                //if it's a number, remove all formatting
                try {
                    double d = cellData.getDoubleValue();
                    cellFormat = d + "";
                    if (cellFormat.endsWith(".0")) {
                        cellFormat = cellFormat.substring(0, cellFormat.length() - 2);
                    }
                } catch (Exception ignored) {
                }
            }
            if (cellFormat.contains("\"\"") && cellFormat.startsWith("\"") && cellFormat.endsWith("\"")) {
                //remove spuriouse quote marks
                cellFormat = cellFormat.substring(1, cellFormat.length() - 1).replace("\"\"", "\"");
            }
            return cellFormat;
        }
        return "";
    }

    // duff names can casue all sorts of problems, best to zap them
    static void removeNamesWithNoRegion(Book book){
        // I hope removeif will work on the names list . . .
        book.getInternalBook().getNames().removeIf(name -> name.getRefersToCellRegion() == null);
    }
}