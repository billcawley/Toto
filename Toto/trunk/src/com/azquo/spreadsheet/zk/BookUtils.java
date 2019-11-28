package com.azquo.spreadsheet.zk;

import com.azquo.DateUtils;
import com.azquo.spreadsheet.CommonReportUtils;
import com.azquo.spreadsheet.LoggedInUser;
import org.apache.commons.lang.math.NumberUtils;
import org.zkoss.poi.ss.usermodel.DateUtil;
import org.zkoss.poi.ss.usermodel.Name;
import org.zkoss.poi.ss.usermodel.Workbook;
import org.zkoss.poi.ss.util.AreaReference;
import org.zkoss.poi.ss.util.CellReference;
import org.zkoss.zss.api.Range;
import org.zkoss.zss.api.Ranges;
import org.zkoss.zss.api.model.Book;
import org.zkoss.zss.api.model.CellData;
import org.zkoss.zss.api.model.Sheet;
import org.zkoss.zss.model.CellRegion;
import org.zkoss.zss.model.SCell;
import org.zkoss.zss.model.SName;
import org.zkoss.zss.model.SSheet;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.*;

/**
 * Created by edward on 09/01/17.
 * <p>
 * Low level ZK manipulation functions.
 */
public class BookUtils {

    public static List<List<String>> nameToStringLists(LoggedInUser loggedInUser, SName sName) {
        return nameToStringLists(loggedInUser, sName, null, 0, 0);
    }

    static List<List<String>> nameToStringLists(LoggedInUser loggedInUser, SName sName, SName repeatRegion, int rowOffset, int colOffset) {
        List<List<String>> toReturn = new ArrayList<>();
        if (sName == null) return toReturn;
        // EFC note - this only applies in the case of repeat regions as called in teh report filler service, really this check should be out there
        // the catch is that row headings might be in the region
        if (repeatRegion != null) {
            //if sName is outside the repeat region, do not add on the row col offsets
            if (sName.getRefersToCellRegion().getRow() < repeatRegion.getRefersToCellRegion().getRow()
                    || sName.getRefersToCellRegion().getRow() >= repeatRegion.getRefersToCellRegion().getRow() + repeatRegion.getRefersToCellRegion().getRowCount()
                    || sName.getRefersToCellRegion().getColumn() < repeatRegion.getRefersToCellRegion().getColumn()
                    || sName.getRefersToCellRegion().getColumn() >= repeatRegion.getRefersToCellRegion().getColumn() + repeatRegion.getRefersToCellRegion().getColumnCount()) {
                rowOffset = 0;
                colOffset = 0;
            }
        }
        CellRegion region = sName.getRefersToCellRegion();
        if (region == null) return toReturn;
        SSheet sheet = sName.getBook().getSheetByName(sName.getRefersToSheetName());
        regionToStringList(loggedInUser,toReturn,region,sheet,rowOffset,colOffset);
        return toReturn;
    }

    static void regionToStringList(LoggedInUser loggedInUser,List<List<String>> toReturn, CellRegion region, SSheet sheet, int rowOffset, int colOffset){
        for (int rowIndex = region.getRow() + rowOffset; rowIndex <= region.getLastRow() + rowOffset; rowIndex++) {
            List<String> row = new ArrayList<>();
            toReturn.add(row);
            for (int colIndex = region.getColumn() + colOffset; colIndex <= region.getLastColumn() + colOffset; colIndex++) {
                SCell cell = sheet.getCell(rowIndex, colIndex);
                if (cell != null) {
                    // being paraniod?
                    if (cell.getType() == SCell.CellType.FORMULA) {
                        //System.out.println("doing the cell thing on " + cell);
                        cell.getFormulaResultType();
                        cell.clearFormulaResultCache();
                    }
                    // I assume non null cell has a non null string value, this may not be true. Also will I get another type of exception?
                    try {
                        // boolean required as sometimes there could be a leftover string value

                        row.add(CommonReportUtils.replaceUserChoicesInQuery(loggedInUser,cell.getStringValue()));
                    } catch (Exception e) {
                        if (!cell.getType().equals(SCell.CellType.BLANK)) {
                            try {
                                String numberGuess = cell.getNumberValue() + "";
                                if (numberGuess.endsWith(".0")) {
                                    numberGuess = numberGuess.substring(0, numberGuess.length() - 2);
                                }
                                if (numberGuess.equals("0")) numberGuess = "";
                                if (cell.getCellStyle().getDataFormat().contains("mm")){
                                    Date javaDate= DateUtil.getJavaDate((double)cell.getNumberValue());
                                    row.add(new SimpleDateFormat("yyyy-MM-dd").format(javaDate));
                                }else{
                                    row.add(numberGuess);
                                }
                            } catch (Exception e2) {
                                /*
                                todo, sort this
java.lang.IllegalStateException: is ERROR, not the one of [STRING, BLANK]
        at org.zkoss.zss.model.impl.AbstractCellAdv.checkFormulaResultType(AbstractCellAdv.java:71)
        at org.zkoss.zss.model.impl.AbstractCellAdv.getStringValue(AbstractCellAdv.java:92)
        at com.azquo.spreadsheet.zk.BookUtils.nameToStringLists(BookUtils.java:79)
        at com.azquo.spreadsheet.zk.BookUtils.nameToStringLists(BookUtils.java:32)

                                 */
                                try {
                                    row.add(cell.getStringValue());
                                } catch (Exception e3) {
                                    row.add("");
                                }
                            }
                        } else {
                            row.add("");
                        }
                    }
                } else {
                    row.add("");
                }
            }
        }

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

    public static CellReference getNameCell(Name sheetName) {
        if (sheetName == null) return null;
        AreaReference aref = new AreaReference(sheetName.getRefersToFormula());
        return aref.getFirstCell();
    }

    public static String rangeToText(int row, int col) {
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
                sCell.setDateValue(DateUtils.getDateFromLocalDateTime(date.atStartOfDay()));
                return;
            }
        }
        if (NumberUtils.isNumber(sValue)) {
            try {
                sCell.setValue(Double.parseDouble(sValue));
            } catch (Exception e) {
                //isNumber seems to allow 100000L   so ignore the exception - it's not a number
            }
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
        names.sort(Comparator.comparing(o -> o.getName().toUpperCase()));
        return names;
    }

    public static List<Name> getNamesForSheet(org.zkoss.poi.ss.usermodel.Sheet sheet) {
        List<Name> names = new ArrayList<>();
        for (int i = 0; i < sheet.getWorkbook().getNumberOfNames(); i++) {
            Name name = sheet.getWorkbook().getNameAt(i);
            try {
                if (sheet.getSheetName().equals(name.getSheetName())) {
                    names.add(name);
                }
            } catch (Exception ignored){
                // name.getSheetName() can throw an exception, ignore it
            }
        }
        names.sort(Comparator.comparing(o -> o.getNameName().toUpperCase()));
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
    static void removeNamesWithNoRegion(Book book) {
        for (SName name : book.getInternalBook().getNames()) {
            if (name.getRefersToCellRegion() == null && name.getRefersToFormula() == null) {
                // how to remove the name?
                try {
                    book.getInternalBook().deleteName(name);
                } catch (Exception e) {
                    //maybe we cannot delete in this way
                }
            }
        }
    }

    public static SName getNameByName(String name, Sheet sheet) {
        //this call is case insensitive - I checked the decompiled code
        SName toReturn = sheet.getBook().getInternalBook().getNameByName(name, sheet.getSheetName());
        if (toReturn != null) {
            return toReturn;

        }
        // should we check the formula refers to the sheet here? I'm not sure. Applies will have been checked for above.
        return sheet.getBook().getInternalBook().getNameByName(name);
    }

    public static Name getNameByName(String name, org.zkoss.poi.ss.usermodel.Sheet sheet) {
        for (int i = 0; i < sheet.getWorkbook().getNumberOfNames(); i++) {
            Name possible = sheet.getWorkbook().getNameAt(i);
            if (sheet.getSheetName().equals(possible.getSheetName()) && possible.getNameName().equalsIgnoreCase(name)) {
                return possible;
            }
        }
        return null;
    }

    static void notifyChangeOnRegion(Sheet sheet, CellRegion region) {
        Range selection = Ranges.range(sheet, region.row, region.column, region.lastRow, region.lastColumn);
        selection.notifyChange();
    }

    public static Name getName(Workbook book, String stringName) {
        int nameCount = book.getNumberOfNames();
        for (int i = 0; i < nameCount; i++) {
            Name name = book.getNameAt(i);
            if (name.getNameName().equalsIgnoreCase(stringName)) {
                return name;
            }
        }
        return null;
    }

    public static List<List<String>> replaceUserChoicesInRegionDefinition(LoggedInUser loggedInUser, SName rangeName){
        List<List<String>> region = BookUtils.nameToStringLists(loggedInUser, rangeName);
        for (int row=0;row < region.size();row++){
            for(int col=0;col < region.get(row).size();col++){
                region.get(row).set(col, CommonReportUtils.replaceUserChoicesInQuery(loggedInUser,region.get(row).get(col)));
            }
        }
        return region;
    }



}