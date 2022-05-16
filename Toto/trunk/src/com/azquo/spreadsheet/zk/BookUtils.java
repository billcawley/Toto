package com.azquo.spreadsheet.zk;

import com.azquo.DateUtils;
import com.azquo.StringLiterals;
import com.azquo.admin.onlinereport.ExternalDataRequest;
import com.azquo.admin.onlinereport.ExternalDataRequestDAO;
import com.azquo.spreadsheet.CommonReportUtils;
import com.azquo.spreadsheet.LoggedInUser;
import io.keikai.model.*;
import org.apache.commons.lang.math.NumberUtils;
import org.zkoss.poi.ss.usermodel.DateUtil;
import org.zkoss.poi.ss.usermodel.Name;
import org.zkoss.poi.ss.usermodel.Row;
import org.zkoss.poi.ss.usermodel.Workbook;
import org.zkoss.poi.ss.util.AreaReference;
import org.zkoss.poi.ss.util.CellReference;
import io.keikai.api.Range;
import io.keikai.api.Ranges;
import io.keikai.api.model.Book;
import io.keikai.api.model.CellData;
import io.keikai.api.model.Sheet;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.*;

/**
 * Created by edward on 09/01/17.
 * <p>
 * Low level ZK manipulation functions.
 */
public class BookUtils {

    public static List<List<String>> nameToStringLists(SName sName) {
        return nameToStringLists(sName, null, 0, 0);
    }

    static List<List<String>> nameToStringLists(SName sName, SName repeatRegion, int rowOffset, int colOffset) {
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
        regionToStringList(toReturn, region, sheet, rowOffset, colOffset);
        return toReturn;
    }

    static void regionToStringList(List<List<String>> toReturn, CellRegion region, SSheet sheet, int rowOffset, int colOffset) {
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
                        row.add(cell.getStringValue());
                    } catch (Exception e) {
                        if (!cell.getType().equals(SCell.CellType.BLANK)) {
                            try {
                                String numberGuess = cell.getNumberValue() + "";
                                if (numberGuess.endsWith(".0")) {
                                    numberGuess = numberGuess.substring(0, numberGuess.length() - 2);
                                }
                                if (numberGuess.equals("0")) numberGuess = "";
                                if (cell.getCellStyle().getDataFormat().contains("mm")) {
                                    Date javaDate = DateUtil.getJavaDate((double) cell.getNumberValue());
                                    row.add(new SimpleDateFormat("yyyy-MM-dd").format(javaDate));
                                } else {
                                    row.add(numberGuess);
                                }
                            } catch (Exception e2) {
                                /*
                                todo, sort this
java.lang.IllegalStateException: is ERROR, not the one of [STRING, BLANK]
        at io.keikai.model.impl.AbstractCellAdv.checkFormulaResultType(AbstractCellAdv.java:71)
        at io.keikai.model.impl.AbstractCellAdv.getStringValue(AbstractCellAdv.java:92)
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

    public static org.apache.poi.ss.util.CellReference getNameCell(org.apache.poi.ss.usermodel.Name sheetName) {
        if (sheetName == null) return null;
        org.apache.poi.ss.util.AreaReference aref = new org.apache.poi.ss.util.AreaReference(sheetName.getRefersToFormula(), null); // try null on the spreadsheet version, wasn't used in older versions of the api
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
        return getNamedRegionForRowAndColumnSelectedSheet(row, col, sheet, StringLiterals.AZDATAREGION);
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
        if (NumberUtils.isNumber(sValue.trim())) {
            try {
                sCell.setValue(Double.parseDouble(sValue.trim()));
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

    public static void localiseNames(Book book) {
        for (SName name : book.getInternalBook().getNames()) {
            if (name.getRefersToSheetName() != null) {
                name.setApplyToSheetName(name.getRefersToSheetName());
            }
        }
    }



    public static List<org.apache.poi.ss.usermodel.Name> getNamesForSheet(org.apache.poi.ss.usermodel.Sheet sheet) {
        List<org.apache.poi.ss.usermodel.Name> names = new ArrayList<>();
        for (org.apache.poi.ss.usermodel.Name name : sheet.getWorkbook().getAllNames()) {
            try {
                if (sheet.getSheetName().equals(name.getSheetName())) {
                    names.add(name);
                }
            } catch (Exception ignored) {
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
        List<SName> toBeDeleted = new ArrayList<>();
        for (SName name : book.getInternalBook().getNames()) {
            if (book.getInternalBook().getSheetByName(name.getRefersToSheetName())==null || (name.getRefersToCellRegion() == null && name.getRefersToFormula() == null)
            ) {
                toBeDeleted.add(name);
              }
        }
        for (SName name:toBeDeleted){
            book.getInternalBook().deleteName(name);
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

    public static CellRegion getNameByName(String nameName, Book book)throws Exception {
        //this call is case insensitive - I checked the decompiled code
        for (SName name: book.getInternalBook().getNames()) {
            if (name.getName().equalsIgnoreCase(nameName)){
                if (name.getRefersToCellRegion()==null){
                    break;
                }
                return name.getRefersToCellRegion();
            }
        }
        throw new Exception("no valid range " + nameName + " found");
    }

    public static io.keikai.api.model.Sheet  getSheetFor(String nameName, Book book){
        //only added because CellRegion does not appear to have a 'getSheet() method
        for (SName name: book.getInternalBook().getNames()) {
            if (name.getName().equalsIgnoreCase(nameName)){
                return book.getSheet(name.getRefersToSheetName());
             }
        }
        return null;
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


    // todo we need to move all of this to proper POI but I need to clarify the issues - the XMLbeans conflict I had to override. Of course Kekai might make this redindant
    public static org.apache.poi.ss.usermodel.Name getName(org.apache.poi.ss.usermodel.Workbook book, String stringName) {
        for (org.apache.poi.ss.usermodel.Name name : book.getAllNames()) {
            if (name.getNameName().equalsIgnoreCase(stringName) && !name.isHidden()) { // hidden names can interfere with things! We only care about what is visible
                return name;
            }
        }
        return null;
    }

    public static org.apache.poi.ss.usermodel.Name getName(org.apache.poi.ss.usermodel.Sheet sheet, String stringName) {
        org.apache.poi.ss.usermodel.Workbook book = sheet.getWorkbook();
        for (org.apache.poi.ss.usermodel.Name name : book.getAllNames()) {
            if (name.getNameName().equalsIgnoreCase(stringName) && !name.isHidden() && name.getSheetName().equals(sheet.getSheetName())) { // hidden names can interfere with things! We only care about what is visible
                return name;
            }
        }
        return null;
    }

    public static List<List<String>> replaceUserChoicesInRegionDefinition(LoggedInUser loggedInUser, SName rangeName) {
        return replaceUserChoicesInRegionDefinition(loggedInUser, rangeName, null, 0, 0);
    }

    public static List<List<String>> replaceUserChoicesInRegionDefinition(LoggedInUser loggedInUser, SName rangeName, SName repeatRegion, int rowOffset, int colOffset) {
        List<List<String>> region = BookUtils.nameToStringLists(rangeName, repeatRegion, rowOffset, colOffset);
        for (List<String> strings : region) {
            for (int col = 0; col < strings.size(); col++) {
                strings.set(col, CommonReportUtils.replaceUserChoicesInQuery(loggedInUser, strings.get(col)));
            }
        }
        return region;
    }

    public static void deleteSheet(Book book, int sheetNumber) {
           book.getInternalBook().deleteSheet(book.getInternalBook().getSheet(sheetNumber));
           removeNamesWithNoRegion(book);

    }

    public static void setNameValue(Book book, String name, String value)throws Exception{
        SName sName = getNameByName(name, book.getSheetAt(0));
        if (sName!=null){
            SCell resultCell = getSnameCell(sName);
            resultCell.setStringValue(value);

        }

    }

    public static void sumifConverter(Book book){
        for (int i=0;i<book.getNumberOfSheets();i++){
            Sheet sheet = book.getSheetAt(i);
            for (int rowNo = 0;rowNo <= sheet.getLastRow(); rowNo++){
                SRow row = sheet.getInternalSheet().getRow(rowNo);
                if (row!=null){
                    Iterator colIt = row.getCellIterator();
                    while (colIt.hasNext()){
                        SCell cell = (SCell)colIt.next();
                        if (cell.getType()== SCell.CellType.FORMULA){
                            String cellFormula = cell.getFormulaValue();
                            int cursor = cellFormula.indexOf("SUM(IF");
                            StringBuffer newCellFormula  = new StringBuffer();
                            int lastCursor = 0;

                            while (cursor >= 0){
                                newCellFormula.append(cellFormula.substring(lastCursor,cursor));
                                cursor +=7;

                                Map<String,String>conditions=new HashMap<>();
                                if(cellFormula.charAt(cursor)=='('){
                                    cursor++;
                                    int nextSumIf =  cellFormula.indexOf("SUM(IF", cursor);
                                    if (nextSumIf< 0){
                                        nextSumIf = cellFormula.length();
                                    }
                                    int equalPos = cellFormula.indexOf("=",cursor);
                                    while (equalPos > 0 && equalPos < nextSumIf){
                                        String condition = cellFormula.substring(cursor, equalPos);
                                        int closebrackets = cellFormula.indexOf(")",equalPos);
                                        if (closebrackets > 0){
                                            conditions.put(condition,cellFormula.substring(equalPos+1,closebrackets));
                                            cursor = closebrackets + 1;
                                            if (cellFormula.substring(cursor,cursor+2).equals("*(")){
                                                cursor+=2;
                                            }
                                            equalPos = cellFormula.indexOf("=",cursor);
                                        }
                                    }
                                    cursor++;//to cover teh comma;
                                    int closeBrackets = cellFormula.indexOf(")",cursor);
                                    if (closeBrackets < 0) {
                                        return; //formula not understood;
                                    }
                                    String target = cellFormula.substring(cursor,closeBrackets);
                                    newCellFormula.append("SUMIFS(" + target);
                                    for (String condition:conditions.keySet()){
                                        newCellFormula.append(","+ condition+","+ conditions.get(condition));
                                    }
                                    newCellFormula.append(")");
                                    lastCursor = closeBrackets + 2;

                                }else{
                                    int equalPos = cellFormula.indexOf("=",cursor);
                                    if(equalPos < 0 ) {
                                        return;
                                    }
                                    String condition = cellFormula.substring(cursor, equalPos);
                                    int commaPos = cellFormula.indexOf(",",equalPos);
                                    if (commaPos > 0){
                                        conditions.put(condition,cellFormula.substring(equalPos+1,commaPos));
                                        cursor = commaPos + 1;
                                    }
                                    int closeBrackets = cellFormula.indexOf(")", commaPos);
                                    String target = cellFormula.substring(cursor,closeBrackets);
                                    newCellFormula.append("SUMIF(" + target);
                                    for (String cond:conditions.keySet()){
                                        newCellFormula.append(","+ cond+","+ conditions.get(cond));
                                    }
                                    newCellFormula.append(")");
                                    lastCursor = closeBrackets + 2;

                                }

                                cursor = cellFormula.indexOf("SUM(IF", lastCursor);
                            }
                            if (lastCursor>0){
                                newCellFormula.append(cellFormula.substring(lastCursor));
                                cell.setFormulaValue(newCellFormula.toString());
                            }
                        }

                    }
                }
            }
        }
    }
    public static boolean  inExternalData(LoggedInUser loggedInUser,Range cell, List<SName>names){
        Book book = cell.getBook();
        for (SName name:book.getInternalBook().getNames()){
            int nameRow = -1;
            int saveRow = -1;
            List<ExternalDataRequest>externalDataRequests = ExternalDataRequestDAO.findForReportId(loggedInUser.getOnlineReport().getId());
            for(ExternalDataRequest externalDataRequest:externalDataRequests){
                boolean found = false;
                Sheet sheet = null;
                CellRegion cellRegion = null;
                for (int i=0;i<book.getNumberOfSheets();i++){
                    sheet = book.getSheetAt(i);
                    if (sheet.getSheetName().equalsIgnoreCase(externalDataRequest.getSheetRangeName())){
                        return true;
                     }
                }
                for (SName sName:names){
                    if (sName.getName().equalsIgnoreCase(externalDataRequest.getSheetRangeName())){
                        return true;

                    }
                }
            }
        }
        return false;
    }

        private static boolean inRange(Range cell, SName name){
         if (cell.getSheet().getSheetName().equals(name.getRefersToSheetName())
                && cell.getRow()>= name.getRefersToCellRegion().getRow()
                && cell.getRow() <= name.getRefersToCellRegion().getLastRow()
                && cell.getColumn() >= name.getRefersToCellRegion().getColumn()
                && cell.getColumn() <= name.getRefersToCellRegion().getLastColumn()){
             return true;
         }
         return false;
     }


}