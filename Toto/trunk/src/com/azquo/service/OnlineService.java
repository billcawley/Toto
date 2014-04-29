package com.azquo.service;

import com.azquo.admindao.UserChoiceDAO;
import com.azquo.adminentities.OnlineReport;
import com.azquo.adminentities.UserChoice;
import org.apache.commons.lang.math.NumberUtils;
import org.apache.poi.hssf.usermodel.HSSFCellStyle;
import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.hssf.util.HSSFColor;
import org.apache.poi.hssf.usermodel.HSSFPalette;


import org.apache.poi.ss.format.CellFormat;
import org.apache.poi.ss.format.CellFormatResult;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.apache.poi.ss.usermodel.CellStyle.*;
import static org.apache.poi.ss.usermodel.CellStyle.ALIGN_GENERAL;
import static org.apache.poi.ss.usermodel.CellStyle.BORDER_THIN;

public final class OnlineService {

    @Autowired
    NameService nameService;

    @Autowired
    UserChoiceDAO userChoiceDAO;

    @Autowired
    ValueService valueService;

    class Range {
        Cell startCell;
        Cell endCell;

    }
    class ShiftData{
        int startRow;
        int startCol;
        int shiftRows;
        int shiftCols;

        ShiftData(int startRow, int startCol, int shiftRows, int shiftCols){
            this.startRow = startRow;
            this.startCol = startCol;
            this.shiftRows = shiftRows;
            this.shiftCols = shiftCols;
        }
    }
     private Workbook wb = null;
    private Sheet azquoSheet = null;
    private Appendable output = null;
     int maxWidth;
    List<Integer> colWidth = new ArrayList<Integer>();
    // now add css for each used style
    Map<String,String>shortStyles = null;


    Map<String, String> rangeNames = null;

    Map<Cell, String> choiceMap = null;
    Map<Cell, Cell> mergedCells = null;
    HSSFPalette hssfColors = null;

    int maxCol = 0;
    int maxHeight = 0;

    private Formatter out;
    private StringBuffer head = null;
    private Formatter sOut = null;//for cell styles
    private short borderBottom = 0;
    private short borderRight = 0;



    private static final int ROWSCALE = 16;
    private static final int COLUMNSCALE = 32;
    private static final HSSFColor HSSF_AUTO = new HSSFColor.AUTOMATIC();

    private static final Map<Short, String> ALIGN = mapFor(ALIGN_LEFT, "left",
            ALIGN_CENTER, "center", ALIGN_RIGHT, "right", ALIGN_FILL, "left",
            ALIGN_JUSTIFY, "left", ALIGN_CENTER_SELECTION, "center");

    private static final Map<Short, String> VERTICAL_ALIGN = mapFor(
            VERTICAL_BOTTOM, "bottom", VERTICAL_CENTER, "middle", VERTICAL_TOP,
            "top");
    // below adjusted dashed to solid
    private static final Map<Short, String> BORDER = mapFor(BORDER_DASH_DOT,
            "solid 1pt", BORDER_DASH_DOT_DOT, "solid 1pt", BORDER_DASHED,
            "solid 1pt", BORDER_DOTTED, "solid 1pt", BORDER_DOUBLE,
            "double 3pt", BORDER_HAIR, "solid 1px", BORDER_MEDIUM, "solid 2pt",
            BORDER_MEDIUM_DASH_DOT, "dashed 2pt", BORDER_MEDIUM_DASH_DOT_DOT,
            "solid 2pt", BORDER_MEDIUM_DASHED, "solid 2pt", BORDER_NONE,
            "1px solid silver", BORDER_SLANTED_DASH_DOT, "solid 2pt", BORDER_THICK,
            "solid 3pt", BORDER_THIN, "solid 1pt");

    private static final Map<Short, String> BORDERSIZE = mapFor(BORDER_DASH_DOT,
            "1", BORDER_DASH_DOT_DOT, "1", BORDER_DASHED,
            "1", BORDER_DOTTED, "1", BORDER_DOUBLE,
            "1", BORDER_HAIR, "1", BORDER_MEDIUM, "2",
            BORDER_MEDIUM_DASH_DOT, "2", BORDER_MEDIUM_DASH_DOT_DOT,
            "2", BORDER_MEDIUM_DASHED, "2", BORDER_NONE,
            "1", BORDER_SLANTED_DASH_DOT, "2", BORDER_THICK,
            "3", BORDER_THIN, "1");



    @SuppressWarnings({"unchecked"})

    private static final Map<String, String> SHORTSTYLES = mapFor(
            "background-color","bc","border-bottom","bb","border-bottom-color","bbc",
            "border-right","br","border-right-color","brc", "color","c", "font-size","fs",
            "font-weight","fw","height","h","left","l","position","p",
            "text-align","ta","top","t","vertical-align","va",
            "width","w","z-index","z");




    private static <K, V> Map<K, V> mapFor(Object... mapping) {
        Map<K, V> map = new HashMap<K, V>();
        for (int i = 0; i < mapping.length; i += 2) {
            map.put((K) mapping[i], (V) mapping[i + 1]);
        }
        return map;
    }


    public String readExcel(LoggedInConnection loggedInConnection, OnlineReport onlineReport) {


        StringBuffer sb = new StringBuffer();
        try {
            wb = WorkbookFactory.create(new FileInputStream(onlineReport.getFilename()));
            output = sb;
            head = new StringBuffer();

            print(loggedInConnection, onlineReport.getId());
        } catch (Exception e) {
            e.printStackTrace();
        }
        head.append("<style>\n");
        for (String className : shortStyles.keySet()){
            head.append("." + shortStyles.get(className) + " {" + className + "}\n");
        }
        printFile("excelStyle.css");
        head.append("</style>\n");
        head.append("<script>\n");
        printFile("online.js");
         head.append("</script>");
        return head.toString() + "</head>\n" + sb.toString();
    }




    private void createMergeMap() {
        mergedCells = new HashMap<Cell, Cell>();
        for (int i = 0; i < azquoSheet.getNumMergedRegions(); i++) {
            CellRangeAddress r = azquoSheet.getMergedRegion(i);
            mergedCells.put(azquoSheet.getRow(r.getFirstRow()).getCell(r.getFirstColumn()), azquoSheet.getRow(r.getLastRow()).getCell(r.getLastColumn()));
            //mergeMap.put(r.)

        }

    }

    private void createNameMap() {
        rangeNames = new HashMap<String, String>();
        for (int i = 0; i < wb.getNumberOfNames(); i++) {
            Name name = wb.getNameAt(i);
            rangeNames.put(name.getNameName().toLowerCase(), name.getRefersToFormula());
        }
        createChoiceMap();
        createMergeMap();
    }

    Cell shiftCell(Cell origCell, int startCol, int shiftCols) {
        if (origCell.getColumnIndex() >= startCol) {
            return azquoSheet.getRow(origCell.getRowIndex()).getCell(origCell.getColumnIndex() + shiftCols);

        }
        return origCell;
    }

    private String cellToString(Cell c) {
        return "$" + (char) (c.getColumnIndex() + 65) + "$" + (c.getRowIndex() + 1);
    }

    private String rangeToString(Range r) {
        String s = "'" + r.startCell.getSheet().getSheetName() + "'!" + cellToString(r.startCell);
        if (r.endCell!=null && r.startCell != r.endCell) {
            return s + ":" + cellToString(r.endCell);
        }
        return s;
    }

    private String adjustRefersToFormula(String formula, int shiftStart, int shiftCols){
        int startPos = formula.indexOf("!") + 1;
        StringBuffer adjusted = new StringBuffer();
        adjusted.append(formula.substring(0, startPos));
        char c = formula.charAt(startPos);
        int num = 0;
        while (startPos < formula.length()) {
            c = formula.charAt(startPos++);
            if (c < 'A') {
                adjusted.append(c);
            } else {
                //convert the column to a number
                int col = c - 65;
                if (col > 32) col = col - 32;//lower to upper
                if (startPos < formula.length() && formula.charAt(startPos) >= 'A') {
                    int nextChar = formula.charAt(startPos++);
                    if (nextChar > 96) nextChar -= 32;//lower to upper
                    col = col * 26 + nextChar - 65;
                }
                //change if required
                if (col >= shiftStart) {
                    col += shiftCols;
                }
                //and convert back to letters
                int nextChar = 64;
                while (col >= 26) {
                    nextChar++;
                    col -= 26;
                }
                if (nextChar > 64) {
                    adjusted.append((char)nextChar);
                }
                adjusted.append((char)(col + 65));
            }
        }
        return adjusted.toString();
    }

    private void adjustNames(int colStart, int colShift) {
        for (int i = 0; i < wb.getNumberOfNames(); i++) {
            Name name = wb.getNameAt(i);
            if (name.getRefersToFormula() != null) {
                name.setRefersToFormula(adjustRefersToFormula(name.getRefersToFormula(), colStart, colShift));
            }
        }
        createChoiceMap();
        createMergeMap();
    }

    private Range interpretRangeName(String cellString) {

        Range range = new Range();
        int bangPos = cellString.indexOf("!");
        if (bangPos < 0) return null;
        String sheetName = cellString.substring(0, bangPos).replace("'", "");
        Sheet sheet = wb.getSheet(sheetName);
        if (sheet == null) return null;
        cellString = cellString.substring(bangPos + 1);
        Cell cell = null;
        if (cellString.indexOf(":") > 0) {
            int breakPos = cellString.indexOf(":");
            range.endCell = interpretCellName(sheet, cellString.substring(breakPos + 1));
            cellString = cellString.substring(0, breakPos);
        }
        range.startCell = interpretCellName(sheet, cellString);
        if (range.endCell == null) {
            range.endCell = range.startCell;
        }
        return range;
    }

    private Cell interpretCellName(Sheet sheet, String cellString) {


        int pos = 0;
        int ch = cellString.charAt(pos++);
        if (ch == '$')  ch = cellString.charAt(pos++);;

        int column = ch - 65;
        if (column > 32) column -=32;
        ch = cellString.charAt(pos++);
        if (ch >= 'A'){
            if (ch >='a') ch -=32;
            column = column * 26 + ch;
            ch = cellString.charAt(pos++);

        }
        if (ch==':') ch = cellString.charAt(pos++);//note the conditional cell formulae (Azquo style) do not have colons
        if (ch == '$')  ch = cellString.charAt(pos++);
        int rowNo = ch - 48;
        while (pos < cellString.length()){
            //no check for a number - assumed to be so!
            ch = cellString.charAt(pos++);
            rowNo = rowNo * 10 + ch - 48;
        }
        rowNo = rowNo-1;
        if (column >= 0 && rowNo >= 0 && sheet.getRow(rowNo) != null) {
            Cell cell = sheet.getRow(rowNo).getCell(column);
            if (cell == null) {
                sheet.getRow(rowNo).createCell(column);
                cell = sheet.getRow(rowNo).getCell(column);
            }
            return cell;
        }


        return null;
    }

    private void createChoiceMap() {
        choiceMap = new HashMap<Cell, String>();
        for (String name : rangeNames.keySet()) {
            if (name.toLowerCase().endsWith("chosen")) {
                String formula = rangeNames.get(name);
                if (formula.indexOf("!") > 0) {
                    Range range = interpretRangeName(formula);
                    if (range.startCell != null && range.endCell == range.startCell) {
                        choiceMap.put(range.startCell, name.substring(0, name.length() - 6).toLowerCase());
                    }
                }
            }
        }
    }

    private void setChoices(LoggedInConnection loggedInConnection, int reportId) {

        for (Cell cell : choiceMap.keySet()) {
            String choiceName = choiceMap.get(cell);
            String choice = rangeNames.get(choiceName + "choice");
            if (choice != null) {
                Range range = interpretRangeName(choice);
                if (range.startCell != null && range.endCell == range.startCell) {

                    UserChoice userChoice = userChoiceDAO.findForUserIdReportIdAndChoice(loggedInConnection.getUser().getId(), reportId, choiceName);
                    if (userChoice!=null){
                        cell.setCellValue(userChoice.getChoiceValue());
                    }
                }
            }
        }
        calculateAll(wb);
    }

    private void calculateAll(Workbook wb) {

        //not sure that this covers contingent calculations correctly
        FormulaEvaluator evaluator = wb.getCreationHelper().createFormulaEvaluator();
        for (int sheetNum = 0; sheetNum < wb.getNumberOfSheets(); sheetNum++) {
            Sheet sheet = wb.getSheetAt(sheetNum);
            for (Row r : sheet) {
                for (Cell c : r) {
                    if (c.getCellType() == Cell.CELL_TYPE_FORMULA) {
                        evaluator.evaluateFormulaCell(c);
                    }
                }
            }
        }


    }

    private String rangeToText(String rangeName) {
        StringBuffer sb = new StringBuffer();
        Range range = interpretRangeName(rangeName);
        if (range.startCell == null) return "error: range not understood " + rangeName;
        boolean firstRow = true;
        for (int rowNo = range.startCell.getRowIndex(); rowNo <= range.endCell.getRowIndex(); rowNo++) {
            if (firstRow) {
                firstRow = false;
            } else {
                sb.append("\n");
            }
            boolean firstCol = true;
            for (int colNo = range.startCell.getColumnIndex(); colNo <= range.endCell.getColumnIndex(); colNo++) {
                if (firstCol) {
                    firstCol = false;
                } else {
                    sb.append("\t");
                }
                sb.append(range.startCell.getSheet().getRow(rowNo).getCell(colNo).getStringCellValue());
            }
        }
        return sb.toString();
    }

    private void setupColwidths() {
        maxWidth = 0;
        colWidth = new ArrayList<Integer>();
        for (int c = 0; c < maxCol; c++) {
            shortStyles.put("left:" + maxWidth + "px", "cp" + c);
            colWidth.add((int) azquoSheet.getColumnWidth(c) / COLUMNSCALE);
            maxWidth += (int) azquoSheet.getColumnWidth(c) / COLUMNSCALE;
        }

    }

    private void setupMaxHeight(){
        maxHeight = 0;
        for (int rowNo = 0; rowNo < azquoSheet.getLastRowNum();rowNo++){
            maxHeight+=azquoSheet.getRow(rowNo).getHeight()/ROWSCALE;
        }
    }

    private void insertRows(Range range, int rowCount) {
           int existingRows = range.endCell.getRowIndex() - range.startCell.getRowIndex() + 1;
        if (existingRows < rowCount) {
            int shiftStart = range.endCell.getRowIndex();
            ShiftData shiftData = new ShiftData(shiftStart - 1, 0, 0, 0);//we are copying the row above the inserts
            adjustConditionalFormatting('R', shiftStart, rowCount - existingRows);
            azquoSheet.shiftRows(shiftStart, azquoSheet.getLastRowNum(), rowCount - existingRows);
            Map<Integer, CellStyle> styleMap = new HashMap<Integer, CellStyle>();
            for (int rowNo = shiftStart; rowNo < shiftStart + rowCount - existingRows; rowNo++) {
                if (azquoSheet.getRow(rowNo) == null) {
                    azquoSheet.createRow(rowNo);
                }
                shiftData.shiftRows = rowNo - shiftStart + 1;
                copyRow(azquoSheet, azquoSheet, azquoSheet.getRow(shiftStart - 1), azquoSheet.getRow(rowNo), styleMap, shiftData);
            }


        }
    }

    private void insertCols(Range range, int colCount) {
        //TODO MOVE HIDDEN COLUMNS TOO!
        Map<Integer, CellStyle> styleMap = new HashMap<Integer, CellStyle>();
        int existingCols = range.endCell.getColumnIndex() - range.startCell.getColumnIndex() + 1;
        if (existingCols < colCount) {
            int shiftStart = range.endCell.getColumnIndex();
            int shiftCols = colCount - existingCols;
            ShiftData shiftData = new ShiftData(0,shiftStart, 0, shiftCols);
            adjustConditionalFormatting('C', shiftStart, shiftCols);
            for (int rowNo = 0; rowNo < azquoSheet.getLastRowNum(); rowNo++) {
                if (azquoSheet.getRow(rowNo) != null) {
                    Row irow = azquoSheet.getRow(rowNo);

                    for (int colNo = irow.getLastCellNum(); colNo >= irow.getFirstCellNum(); colNo--) {
                        if (colNo >= shiftStart && irow.getCell(colNo) != null) {
                            if (irow.getCell(colNo + shiftCols) == null) {
                                irow.createCell(colNo + shiftCols);
                            }
                            copyCell(irow.getCell(colNo), irow.getCell(colNo + shiftCols), styleMap, shiftData);
                            irow.removeCell(irow.getCell(colNo));
                        }
                    }
                    if (irow.getCell(irow.getFirstCellNum()) != null){
                        CellStyle style =getRowStyle(irow);
                        Cell modelCell = irow.getCell(shiftStart - 1);
                        if (modelCell != null) {
                               shiftData.startCol--;// we are moving the column before the insert
                               for (int colNo = shiftStart; colNo < shiftStart + colCount - existingCols; colNo++) {
                                shiftData.shiftCols = colNo - shiftStart + 1;
                                copyCell(modelCell, irow.createCell(colNo), styleMap, shiftData);
                            }
                        }else{
                            if (style != null) {
                                for (int colNo = shiftStart; colNo < shiftStart + colCount - existingCols; colNo++) {
                                    irow.createCell(colNo);
                                    irow.getCell(colNo).setCellStyle(style);
                                }
                            }
                        }
                    }
                }
            }
            for (int colNo = maxCol - 1; colNo >= shiftStart; colNo--) {
                azquoSheet.setColumnWidth(colNo + shiftCols, azquoSheet.getColumnWidth(colNo));
            }
            for (int colNo = shiftStart; colNo < shiftStart + shiftCols; colNo++) {
                azquoSheet.setColumnWidth(colNo, azquoSheet.getColumnWidth(shiftStart - 1));
            }
            maxCol += shiftCols;

            adjustNames(shiftStart, shiftCols);
            setupColwidths();
               //TODO  Check merged cells.   Maybe they should be left as is.
        }
    }

    private void adjustConditionalFormatting(int rcFlag, int shiftStart, int shiftCount){
        String formatRange = rangeNames.get("az_conditionalformatting");
        if (formatRange != null){
            Range cFormatRange = interpretRangeName(formatRange);
            Sheet cFormatSheet = cFormatRange.startCell.getSheet();
            int col = cFormatRange.startCell.getColumnIndex() + 1;
            for (int rowNo = cFormatRange.startCell.getRowIndex(); rowNo <= cFormatRange.endCell.getRowIndex();rowNo++){
                Cell conditionCell = cFormatSheet.getRow(rowNo).getCell(col);
                String condition = conditionCell.getStringCellValue();
                String out = "";
                for (int i = 0; i<condition.length();i++) {
                    int c = condition.charAt(i);
                    if (c >= 'A') {
                        if (c > 'a'){
                            c = c - 32; //lower case to upper case
                        }
                        if (rcFlag == 'C' && c >= shiftStart + 65) {
                            c += shiftCount;
                        }
                        out += (char) c;
                    } else if (c >= '1' && c <= '9') {
                        c = c - 48;
                        boolean isNumber = true;
                        while (isNumber && i < condition.length() - 1) {
                            int nextChar = condition.charAt(i + 1);
                            if (nextChar >= '0' && nextChar <= '9') {
                                c = c * 10 + nextChar - 48;
                                i++;
                            } else {
                                isNumber = false;
                            }
                        }
                        if (rcFlag == 'R' && c >= shiftStart){
                            c+= shiftCount;
                        }
                        out += c + "";
                    } else{
                        out += (char) c;
                    }
                }
                conditionCell.setCellValue(out);
            }
        }

    }

    private void fillRange(String regionName, String fillText) {
        boolean shapeAdjusted = false;
        String regionFormula = rangeNames.get(regionName);
        if (regionFormula == null) {
            return;
        }
        Range range = interpretRangeName(regionFormula);
        String[] rows  = fillText.split("\n", -1);
        int rowNo = range.startCell.getRowIndex();
        for (String row:rows) {
            String[] vals = row.split("\t", -1);
            int colCount = vals.length;
            if (!shapeAdjusted) {
                int rowCount = rows.length;
                insertRows(range, rowCount);
                insertCols(range, colCount);
                createNameMap();
                shapeAdjusted = true;
            }
            Row aRow = azquoSheet.getRow(rowNo);
            int colNo = range.startCell.getColumnIndex();
            for (String val:vals){
                if (aRow.getCell(colNo) == null) aRow.createCell(colNo);
                if (val.equals("0.0")) val = "";
                if (val.endsWith("%") && NumberUtils.isNumber(val.substring(0, val.length() - 1))){
                        aRow.getCell(colNo).setCellValue(Double.parseDouble(val.substring(0,val.length()-1))/100);
                }else{
                    if (NumberUtils.isNumber(val)) {
                        aRow.getCell(colNo).setCellValue(Double.parseDouble(val));
                    } else {
                        aRow.getCell(colNo).setCellValue(val);
                    }
                }
                colNo++;
            }
            rowNo++;
        }

    }

    private String fillRegion(LoggedInConnection loggedInConnection, String region) throws Exception {

        int filterCount = optionNumber(region, "hiderows");
        if (filterCount == 0)
            filterCount = -1;//we are going to ignore the row headings returned on the first call, but use this flag to get them on the second.
        int maxRows = optionNumber(region, "maxrows");

        String headingsFormula = rangeNames.get("az_rowheadings" + region);
        if (headingsFormula == null) {
            return "error: no range az_RowHeadings" + region;
        }
        String headings = rangeToText(headingsFormula);
        if (headings.startsWith("error:")) {
            return headings;
        }
        String result = valueService.getFullRowHeadings(loggedInConnection, region, headings);
        if (result.startsWith("error:")) return result;
        //don't bother to display yet - maybe need to filter out or sort
        headingsFormula = rangeNames.get("az_columnheadings" + region);
        if (headingsFormula == null) {
            return "error: no range az_ColumnHeadings" + region;
        }
        headings = rangeToText(headingsFormula);
        if (headings.startsWith("error:")) {
            return headings;
        }
        result = valueService.getColumnHeadings(loggedInConnection, region, headings);
        if (result.startsWith("error:")) return result;
        fillRange("az_displaycolumnheadings" + region, result);
        headingsFormula = rangeNames.get("az_context" + region);
        if (headingsFormula == null) {
            return "error: no range az_Context" + region;
        }
        headings = rangeToText(headingsFormula);
        if (headings.startsWith("error:")) {
            return headings;
        }
        result = valueService.getDataRegion(loggedInConnection, headings, region, filterCount, maxRows);
        if (result.startsWith("error:")) return result;
        fillRange("az_dataregion" + region, result);
        result = valueService.getRowHeadings(loggedInConnection, region, headings, filterCount);
        fillRange("az_displayrowheadings" + region, result);
        //then percentage, sort, trimrowheadings, trimcolumnheadings, setchartdata


        return "";
    }

    private int optionNumber(String region, String optionName) {
        String option = hasOption(region, optionName);
        if (option == null) return 0;
        int optionValue = 0;
        try {
            optionValue = Integer.parseInt(option);
        } catch (Exception e) {

        }
        return optionValue;
    }


    private String hasOption(String region, String optionName) {
        String regionFormula = rangeNames.get("az_options" + region);
        if (regionFormula == null)
            return null;
        Range range = interpretRangeName(regionFormula);
        if (range == null) return null;
        String options = range.startCell.getStringCellValue();
        int foundPos = options.toLowerCase().indexOf(optionName.toLowerCase());
        if (foundPos < 0) {
            return null;
        }
        options = options.substring(foundPos + optionName.length() + 1).trim();//allow for a space or '=' at the end of the option name
        foundPos = options.indexOf(",");
        if (foundPos > 0) {
            options = options.substring(0, foundPos);
        }
        return options;
    }

    private CellStyle getRowStyle(Row row){
        CellStyle style;
        if (wb instanceof HSSFWorkbook) {
            style = ((HSSFRow) row).getRowStyle();
        } else {
            style = ((XSSFRow) row).getRowStyle();
        }
        return style;

    }

    private List<String> interpretList(String list){
        //expects a list  "aaaa","bbbb"  etc, though it will pay attention only to the quotes
        List<String> items = new ArrayList<String>();
        StringBuffer item = new StringBuffer();
        boolean inItem = false;
        int pos = 0;
        while (pos < list.length()){
            char c = list.charAt(pos++);
            if (c == '\"') {
                if (inItem) {
                    inItem = false;
                    items.add(item.toString());
                    item = new StringBuffer();
                } else {
                    inItem = true;
                }
            }else {
                if (inItem) item.append((char) c);
            }
        }
        return items;

    }

    private String addOption(String item, String selected){
        String content = "<option value = \"" + item + "\"";
        if (item.equals(selected)) {
            content += " selected";

        }
        content += ">" + item + "</option>\n";
        return content;
    }

    private void getMaxCol(){

        Iterator<Row> rows = azquoSheet.rowIterator();
        maxCol=0;
        while (rows.hasNext()) {
            Row row = rows.next();
            //maxHeight += (int) row.getHeight() / ROWSCALE;
            int lastColWithData = row.getLastCellNum();

            if (lastColWithData > maxCol) {

                maxCol = lastColWithData;
            }

        }

    }

    public void print(LoggedInConnection loggedInConnection, int reportId) throws Exception {


        out = new Formatter(output);
        if (wb instanceof HSSFWorkbook) {
            hssfColors = ((HSSFWorkbook) wb).getCustomPalette();
        }

        //head.append("<?xml version=\"1.0\" encoding=\"utf-8\" ?>\n");
        head.append("<!DOCTYPE html PUBLIC \"-//W3C//DTD HTML 4.01 Transitional//EN\" \"http://www.w3.org/TR/html4/loose.dtd\">\n");
        head.append("<html>\n");
        head.append("<head>\n");
        head.append("<meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\" />\n");
        head.append("<script type=\"text/javascript\">\n");
        head.append("\n");
        head.append("function chosen(divName){\n");
        head.append("document.getElementById(\"editedName\").value = divName;\n");
        head.append("document.getElementById(\"editedValue\").value = document.getElementById(divName).value;\n");
        head.append("document.azquoform.submit();\n");
        head.append("}\n");
        head.append("   </script>\n");


         azquoSheet = wb.getSheetAt(0);
        shortStyles = new HashMap<String, String>();
        createNameMap();
        setChoices(loggedInConnection, reportId);
        calculateAll(wb);
        loadData(loggedInConnection);
        calculateAll(wb);
        calcConditionalFormats();
         //find the cells that will be used to make choices...
        getMaxCol();
        createNameMap();
        setupColwidths();
        setupMaxHeight();




        out.format("   <body>%n  <div class=\"excelDefaults\" onkeydown =\"keyDown()\"  style=\"height:%spx;width:%spx;\">%n", maxHeight, maxWidth);
        out.format("   <form name=\"azquoform\" method=\"post\">%n");
        out.format("   <input type=\"hidden\" name=\"reportid\" id=\"reportId\" value=\"%s\"/>%n", reportId);
        out.format("   <input type=\"hidden\" name=\"connectionid\" id=\"connectionId\" value=\"%s\"/>%n", loggedInConnection.getConnectionId());
        out.format("   <input type=\"hidden\" name=\"editedname\" id=\"editedName\" value=\"\"/>%n");
        out.format("   <input type=\"hidden\" name=\"editedvalue\" id=\"editedValue\" value=\"\"/>%n");
         out.format("   </form>%n");
        out.format("    <div id=\"selector\" class=\"selector\"></div>%n");

        Iterator<Row> rows = azquoSheet.rowIterator();

        int rowNo = 0;
        int cellTop = 0;
        while (rowNo < azquoSheet.getLastRowNum()) {

            int cellLeft = 0;
            Row row = azquoSheet.getRow(rowNo);
            if (!row.getZeroHeight()) {
                shortStyles.put("top:" + cellTop + "px", "rp" + rowNo);
                int rowHeight = (int) row.getHeight() / ROWSCALE;
                for (int i = 0; i < maxCol; i++) {
                    Cell cell = row.getCell(i);
                    if (!azquoSheet.isColumnHidden(i)) {

                        String content = "&nbsp;";
                        String attrs = "";
                        attrs = "";
                        StringBuffer cellClass = new StringBuffer();
                        sOut = new Formatter(cellClass);
                          boolean cellRotated = false;
                        if (cell != null) {
                            CellStyle style = cell.getCellStyle();
                            if (cell.getCellType() == Cell.CELL_TYPE_STRING && style.getAlignment()==0){
                                style.setAlignment(ALIGN_LEFT); //Excel defaults text alignment left, numbers to the right

                            }
                                setCellClass(rowNo, i, cell);
                              //Set the value that is rendered for the cell
                            //also applies the format
                            CellFormat cf = CellFormat.getInstance(
                                    style.getDataFormatString());
                            CellFormatResult result = cf.apply(cell);
                            content = result.text;
                            if (content.equals("")) content = "&nbsp;";
                            if (style.getRotation() == 90) {
                                content = "<div class=\"r90\">" + content + "</div>";
                            }
                        } else {
                            setCellClass(rowNo, i, null);//or GetRowStyle??
                            //if (row.getCell(row.getFirstCellNum()) != null) {
                            //    borderBottomType = row.getCell(row.getFirstCellNum()).getCellStyle().getborderBottomType();//line up blanks with other cells in the line
                           // }
                        }

                        int cellHeight = rowHeight - borderBottom;
                        int cellWidth = colWidth.get(i) - borderRight;
                        if (mergedCells.get(cell) != null) {
                            int r = row.getRowNum();
                            Cell lastCell = mergedCells.get(cell);
                            while (r++ < lastCell.getRowIndex()) {
                                cellHeight += azquoSheet.getRow(r).getHeight() / ROWSCALE;

                            }
                            int c=i;
                            while (c++ < lastCell.getColumnIndex()) {
                                cellWidth += azquoSheet.getColumnWidth(c) / COLUMNSCALE;
                            }
                            addStyle("z-index","1");
                              //TODO setting the background color to white when no specific colour set. - is this condition correct? .
                            if (cell.getCellStyle().getFillBackgroundColor() == 64) {
                                addStyle("background-color","white");
                            }
                        }
                        addStyle("position","absolute");
                        addStyle("width",cellWidth + "px");
                        if (choiceMap.get(cell) != null) {
                            //create a select list
                            String choiceName = choiceMap.get(cell);
                            String choice = rangeNames.get(choiceName + "choice");
                            if (choice != null) {
                                Range range = interpretRangeName(choice);
                                if (range.startCell != null && range.endCell == range.startCell) {

                                    List<com.azquo.memorydb.Name> choiceList = new ArrayList<com.azquo.memorydb.Name>();
                                    String cellChoice = range.startCell.getStringCellValue();
                                    List<String> constants = new ArrayList<String>();
                                    if (cellChoice.startsWith("\"")){
                                        constants = interpretList(cellChoice);


                                    }else {
                                        try {
                                            String error = nameService.interpretName(loggedInConnection, choiceList, range.startCell.getStringCellValue());
                                        } catch (Exception e) {
                                            //TODO think what to do !
                                        }
                                    }
                                    if (constants.size() > 0 || choiceList.size() > 0) {

                                        String origContent = content;
                                        //TODO  SORT OUT THE BACKGROUND COLOUR IF NOT WHITE

                                        content = "<select onchange=\"chosen('" + choiceName + "')\" id=\"" + choiceMap.get(cell) + "\" class=\"" + cellClass + "\"  style=\"left:0;border:0;background-color:white;\">\n";
                                        content += "<option value = \"\"></option>";

                                        for (String constant:constants){
                                           content += addOption(constant, origContent);
                                        }
                                        for (com.azquo.memorydb.Name name : choiceList) {
                                            content += addOption(name.getDefaultDisplayName(), origContent);
                                        }
                                        content += "</select>";

                                    }else{
                                        content = "&nbsp;";
                                        cell.setCellValue("");
                                    }
                                }
                            }
                        }
                        cellClass.append("rp" + rowNo + " cp" + i + " ");
                        addStyle("height", cellHeight + "px");
                        out.format("   <div class=\"%s\"  id=\"cell" + rowNo + "-" + i + "\">  %s </div>%n", cellClass, content);
                        //out.format("    <td class=%s %s>%s</td>%n", styleName(style),
                        //        attrs, content);
                        cellLeft += colWidth.get(i);
                    }
                }
                cellTop += rowHeight;
            }
            rowNo++;
        }
        List lst = wb.getAllPictures();
        for (Iterator it = lst.iterator(); it.hasNext(); ) {
            PictureData pict = (PictureData) it.next();
            String ext = pict.suggestFileExtension();
            byte[] data = pict.getData();
            if (ext.equals("jpeg")) {
                int j = 1;
                //FileOutputStream out = new FileOutputStream("pict.jpg");
                //out.write(data);
                //out.close();
            }
        }

        out.format("</div>%n <body>%n </html>");


    }



    private void addStyle( String styleName, String styleValue) {
        String fullstyle = styleName + ":" + styleValue;
        String shortStyle =shortStyles.get(fullstyle.toLowerCase());
        if (shortStyle ==null) {

            String shortName = SHORTSTYLES.get(styleName.toLowerCase());
            if (shortName == null){
                shortName=styleName;
            }
            shortStyle = shortName + styleValue.replace(" ","").replace("#","");
            shortStyles.put(fullstyle.toLowerCase(), shortStyle.toLowerCase());
        }
        sOut.format(shortStyle + " ");


    }


    private void  colorStyles(CellStyle style) {

        if (wb instanceof HSSFWorkbook) {
            HSSFCellStyle cs = (HSSFCellStyle) style;
            //out.format("  /* fill pattern = %d */%n", cs.getFillPattern());
            HSSFstyleColor("background-color", cs.getFillForegroundColor());
            HSSFstyleColor("color", cs.getFont(wb).getColor());
            HSSFstyleColor("border-right-color", cs.getRightBorderColor());
            HSSFstyleColor("border-bottom-color", cs.getBottomBorderColor());
         } else {
            XSSFCellStyle cs = (XSSFCellStyle) style;
            XSSFstyleColor("background-color", cs.getFillForegroundXSSFColor());
            XSSFstyleColor("text-color", cs.getFont().getXSSFColor());
        }
    }

    private void XSSFstyleColor(String attr, XSSFColor color) {
        if (color == null || color.isAuto())
            return;

        byte[] rgb = color.getRgb();
        if (rgb == null) {
            return;
        }

        // This is done twice -- rgba is new with CSS 3, and browser that don't
        // support it will ignore the rgba specification and stick with the
        // solid color, which is declared first
        StringBuffer sb = new StringBuffer();
        Formatter tempout = new Formatter(sb);
        tempout.format("#%02x%02x%02x;", rgb[0], rgb[1], rgb[2]);
        addStyle(attr, tempout.toString());
        byte[] argb = color.getARgb();
        if (argb == null) {
            return;
        }
        sb = new StringBuffer();
        tempout = new Formatter(sb);
        tempout.format("rgba(0x%02x, 0x%02x, 0x%02x, 0x%02x)", argb[3], argb[0], argb[1], argb[2]);
        addStyle(attr, tempout.toString());
    }

    private String hex2(short i){
        if (i < 16){
            return "0" + Integer.toHexString(i);
        }else{
            return Integer.toHexString(i);
        }
    }

    private String rgbToHex(short[]rgb){
        return "#" + hex2(rgb[0]) + hex2(rgb[1]) + hex2(rgb[2]);
    }

    private void HSSFstyleColor(String attr, short index) {
        HSSFColor color = hssfColors.getColor(index);
        if (index == HSSF_AUTO.getIndex() || color == null) {
            addStyle(attr,"/* index = " + index + " */");
        } else {
            short[] rgb = color.getTriplet();
            addStyle(attr, rgbToHex(rgb));
        }
    }






    private void fontStyle(CellStyle style) {
        Font font = wb.getFontAt(style.getFontIndex());

        if (font.getBoldweight() >= Font.BOLDWEIGHT_NORMAL)
            addStyle("font-weight", "bold");
        if (font.getItalic())
            addStyle("font-style", "italic");

        int fontheight = font.getFontHeightInPoints();
        if (fontheight == 9) {
            //fix for stupid ol Windows
            fontheight = 10;
        }
        addStyle("font-size",fontheight + "px");

        // Font color is handled with the other colors
    }

    private void setCellClass(int row, int col, Cell cell) {
        CellStyle style = null;
        if (cell==null) {
            style = getRowStyle(azquoSheet.getRow(row));
            if (style==null){
                style = wb.getCellStyleAt((short) 0);
            }
        }else {
            style = cell.getCellStyle();
        }
        styleOut("text-align", style.getAlignment(), ALIGN);
        styleOut("vertical-align", style.getAlignment(), VERTICAL_ALIGN);
        fontStyle(style);
         short borderRightType = style.getBorderRight();
         short borderBottomType = style.getBorderBottom();
         if (borderBottomType == 0) {

            CellStyle nextStyle = style;
            Row nextRow = azquoSheet.getRow(row + 1);
            if (nextRow != null) {
                Cell below = nextRow.getCell(col);
                if (below != null) {
                    nextStyle = below.getCellStyle();
                }
            }
            borderBottomType = nextStyle.getBorderTop();
        }
        if (borderRightType==0){
            CellStyle nextStyle = style;
            Cell toTheRight = azquoSheet.getRow(row).getCell(col + 1);
            if (toTheRight!=null) {
                nextStyle = toTheRight.getCellStyle();
            }
            borderRightType = nextStyle.getBorderLeft();
        }
        styleOut("border-right", borderRightType, BORDER);
        styleOut("border-bottom", borderBottomType, BORDER);
         borderBottom = 0;
        borderRight = 0;
        try {
            borderBottom = (short)((BORDERSIZE.get(borderBottomType)).charAt(0) - 48);//tried the map as Short, Short, but had casting problems
            borderRight = (short)((BORDERSIZE.get(borderRightType)).charAt(0) - 48);
         }catch(Exception e){
            e.printStackTrace();

        }

        colorStyles(style);
     }

    private <K> void styleOut(String attr, K key, Map<K, String> mapping) {
        String value = mapping.get(key);
        if (value != null) {
            addStyle(attr, value);
        }
    }

    private static int ultimateCellType(Cell c) {
        int type = c.getCellType();
        if (type == Cell.CELL_TYPE_FORMULA)
            type = c.getCachedFormulaResultType();
        return type;
    }


    private String alignStyle(Cell cell, CellStyle style) {
        if (style.getAlignment() == ALIGN_GENERAL) {
            switch (ultimateCellType(cell)) {
                case Cell.CELL_TYPE_STRING:
                    addStyle("text-align","left");
                case Cell.CELL_TYPE_BOOLEAN:
                case Cell.CELL_TYPE_ERROR:
                    addStyle("text-align","center");
                case Cell.CELL_TYPE_NUMERIC:
                default:
                    // "right" is the default
                    break;
            }
        }
        return "";
    }


    public  void copyRow(Sheet srcSheet, Sheet destSheet, Row srcRow, Row destRow, Map<Integer, CellStyle> styleMap, ShiftData shiftData) {
        Set<CellRangeAddress> mergedRegions = new TreeSet<CellRangeAddress>();
        destRow.setHeight(srcRow.getHeight());
        for (int j = srcRow.getFirstCellNum(); j <= srcRow.getLastCellNum(); j++) {
            Cell oldCell = srcRow.getCell(j);
            Cell newCell = destRow.getCell(j);
            if (oldCell != null) {
                if (newCell == null) {
                    newCell = destRow.createCell(j);
                }
                copyCell(oldCell, newCell, styleMap, shiftData);
                CellRangeAddress mergedRegion = getMergedRegion(srcSheet, srcRow.getRowNum(), (short) oldCell.getColumnIndex());
                if (mergedRegion != null) {
                    CellRangeAddress newMergedRegion = new CellRangeAddress(mergedRegion.getFirstRow(), mergedRegion.getFirstColumn(), mergedRegion.getLastRow(), mergedRegion.getLastColumn());
                    if (isNewMergedRegion(newMergedRegion, mergedRegions)) {
                        mergedRegions.add(newMergedRegion);
                        destSheet.addMergedRegion(newMergedRegion);
                    }
                }
            }
        }

    }

    public void copyCell(Cell oldCell, Cell newCell, Map<Integer, CellStyle> styleMap, ShiftData shiftData) {
        if (styleMap != null) {
            if (oldCell.getSheet().getWorkbook() == newCell.getSheet().getWorkbook()) {
                newCell.setCellStyle(oldCell.getCellStyle());
            } else {
                int stHashCode = oldCell.getCellStyle().hashCode();
                CellStyle newCellStyle = styleMap.get(stHashCode);
                if (newCellStyle == null) {
                    newCellStyle = newCell.getSheet().getWorkbook().createCellStyle();
                    newCellStyle.cloneStyleFrom(oldCell.getCellStyle());
                    styleMap.put(stHashCode, newCellStyle);
                }
                newCell.setCellStyle(newCellStyle);
            }
        }
        switch (oldCell.getCellType()) {
            case Cell.CELL_TYPE_STRING:
                newCell.setCellValue(oldCell.getStringCellValue());
                break;
            case Cell.CELL_TYPE_NUMERIC:
                newCell.setCellValue(oldCell.getNumericCellValue());
                break;
            case Cell.CELL_TYPE_BLANK:
                newCell.setCellType(Cell.CELL_TYPE_BLANK);
                break;
            case Cell.CELL_TYPE_BOOLEAN:
                newCell.setCellValue(oldCell.getBooleanCellValue());
                break;
            case Cell.CELL_TYPE_ERROR:
                newCell.setCellErrorValue(oldCell.getErrorCellValue());
                break;
            case Cell.CELL_TYPE_FORMULA:
                newCell.setCellFormula(adjustFormula(oldCell.getCellFormula(), shiftData));
                break;
            default:
                break;
        }

    }

    private String adjustFormula(String calc, ShiftData shiftData){



        int fromPos = 0;
        StringBuffer output = new StringBuffer();
        Pattern p = Pattern.compile("[\\+\\-/\\*\\(\\):]"); // only simple maths allowed at present
        Matcher m = p.matcher(calc);
        int startPos = 0;
        while (m.find()) {
            String opfound = m.group();
            char thisOp = opfound.charAt(0);

            int pos = m.start();
            String namefound = calc.substring(startPos, pos).trim();
            if (namefound.length() > 0) {

                output.append(adjustTerm(namefound, shiftData));
             }
            output.append(m.group());
            startPos = m.end();


        }
        // the last term...
        if (calc.substring(startPos).trim().length() > 0) {
            output.append(adjustTerm(calc.substring(startPos).trim(), shiftData));
        }


        return output.toString();
    }


     String adjustTerm(String term, ShiftData shiftData){


         int chr = term.charAt(0);
         if (chr < 'A' || term.length() < 2){
             return term;
         }
         Name name = wb.getName(term);
         if (name!=null){
             return term;
         }
         boolean fixedCol = false;
         boolean fixedRow = false;
         int pos =1;
         if (chr == '$'){
             fixedCol = true;
             pos++;
             term = term.substring(1);
             chr = term.charAt(pos++);
             if (chr >= 'a'){
                 chr = chr-32;
             }
         }
         int colNo = chr - 65;
         int rowNo = -1;
         chr = term.charAt(pos++);
         if (chr >= 'a') {
             chr = chr - 32;
         }
         if (chr >= 'A') {
             colNo = colNo * 26 + chr - 65;
            if (term.length() == pos) {
                 return term;
             }
             chr = term.charAt(pos++);
         }
         if (chr=='$'){
             fixedRow = true;
             if (term.length() == pos) {
                 return term;
             }
             chr = term.charAt(pos++);
         }
         rowNo = chr - 48;
         if (rowNo > 9 || rowNo < 1){
             return term;
         }
         while (pos < term.length()){
             chr = term.charAt(pos++);
             if (chr <'0' || chr > '9'){
                 return term;
               }
             rowNo = rowNo *10 + chr - 48;
         }
         //note that rowNo is the external value so remove an = sign
         if (!fixedRow && shiftData.shiftRows > 0 && rowNo  > shiftData.startRow){
             rowNo += shiftData.shiftRows;
         }
         if (!fixedCol && shiftData.shiftCols > 0 && colNo >= shiftData.startCol){
             colNo += shiftData.shiftCols;
         }
         String output = "";
         if (fixedCol) {
             output = "$";
         }
         if (colNo >=26){
             colNo -= 26;
             char first = 'A';
             while (colNo >= 26){
                 colNo -=26;
                 first++;
             }
             output+=first;
         }
         output += (char) (colNo + 65);
         if (fixedRow){
             output += "$";
         }
         output += rowNo + "";
         return output;
     }


    public static CellRangeAddress getMergedRegion(Sheet sheet, int rowNum, short cellNum) {
        for (int i = 0; i < sheet.getNumMergedRegions(); i++) {
            CellRangeAddress merged = sheet.getMergedRegion(i);
            if (merged.isInRange(rowNum, cellNum)) {
                return merged;
            }
        }
        return null;
    }

    private static boolean isNewMergedRegion(CellRangeAddress newMergedRegion, Collection<CellRangeAddress> mergedRegions) {
        return !mergedRegions.contains(newMergedRegion);
    }

    public void setUserChoice(int userId, int reportId, String choiceName, String choiceValue) {
        UserChoice userChoice = userChoiceDAO.findForUserIdReportIdAndChoice(userId, reportId, choiceName);
        if (userChoice == null) {
            userChoice = new UserChoice(0, userId, reportId, choiceName, choiceValue, new Date());
            userChoiceDAO.store(userChoice);
        } else {
            if (!choiceValue.equals(userChoice.getChoiceValue())) {
                userChoice.setChoiceValue(choiceValue);
                userChoice.setTime(new Date());
                userChoiceDAO.store(userChoice);
            }
        }
    }

    public void loadData(LoggedInConnection loggedInConnection) throws Exception {


        Iterator<Row> rows = azquoSheet.rowIterator();
        calculateAll(wb);
        for (int i = 0; i < wb.getNumberOfNames(); i++) {
            Name name = wb.getNameAt(i);
            String rangeName = name.getNameName();
            if (rangeName.toLowerCase().startsWith("az_dataregion")) {
                String regionName = rangeName.substring(13).toLowerCase();
                fillRegion(loggedInConnection, regionName);
            }
        }
    }

    private void calcConditionalFormats() {
            /* this routine uses the Azquo range "az_ConditionalFormats to calculate conditional formats, since the Apacne POI does not currently nandle them
            In the azquo definition, the first column is a set of range names to which the conditions apply, and the second is a series of conditions:formats separated by commas
            the conditions consist of the operators < > = & | and cell references with * as 'copy the target cell'.

            e.g.   >=*16:*16   would mean that, if the target cell is >= the cell in row 16 of the same column, adopt the format of that cell.
              a set of three conditions might be
                >=*16:*16,<*16&>=*17:d17,<*17:d18   (note that the result format might be fixed or variable

             */
        String formatRange = rangeNames.get("az_conditionalformatting");
        if (formatRange == null){
            return;
        }
        Range cFormatRange = interpretRangeName(formatRange);
        int col = cFormatRange.startCell.getColumnIndex();
        for (int row = cFormatRange.startCell.getRowIndex(); row <= cFormatRange.endCell.getRowIndex(); row++) {
            Sheet cfSheet = cFormatRange.startCell.getSheet();
            String regionName = cfSheet.getRow(row).getCell(col).getStringCellValue();
            String cFormat = cfSheet.getRow(row).getCell(col+1).getStringCellValue();
            Name dataRangeName = wb.getName(regionName);
            if (dataRangeName != null) {
                Range dataRange = interpretRangeName(dataRangeName.getRefersToFormula());
                for (int rowNo = dataRange.startCell.getRowIndex(); rowNo <= dataRange.endCell.getRowIndex(); rowNo++) {
                    for (int colNo = dataRange.startCell.getColumnIndex(); colNo <= dataRange.endCell.getColumnIndex(); colNo++) {
                        StringTokenizer rules = new StringTokenizer(cFormat, ",");
                        Cell modelCell = null;
                        while (modelCell == null && rules.hasMoreTokens()) {
                            Cell targetCell = azquoSheet.getRow(rowNo).getCell(colNo);
                            StringTokenizer st = new StringTokenizer(rules.nextToken(), ":");
                            if (st.countTokens() == 2) {
                                modelCell = calcRule(st.nextToken(), st.nextToken(), targetCell);
                            }
                            if (modelCell!=null){
                                targetCell.setCellStyle(modelCell.getCellStyle());
                            }
                        }
                    }
                }

            }

        }

    }

    Cell interpretRuleCell(String ruleCell, Cell target) {
        if (ruleCell.startsWith("*")) {
            return interpretCellName(azquoSheet, (char) (target.getColumnIndex() + 65) + ruleCell.substring(1));

        }
        if (ruleCell.endsWith("*")) {
            return interpretCellName(azquoSheet, ruleCell.substring(ruleCell.length() - 1) + (target.getRowIndex() + 1));

        }
        return interpretCellName(azquoSheet, ruleCell);

    }

    Cell calcRule(String condition, String formatModel, Cell cell) {
        if (condition.indexOf("|") > 0) {
            StringTokenizer orSplit = new StringTokenizer("|");
            while (orSplit.hasMoreTokens()) {
                if (calcRule(orSplit.nextToken(), formatModel, cell) != null)
                    return interpretRuleCell(formatModel, cell);
            }
            return null;
        }
        if (condition.indexOf("&") > 0) {
            StringTokenizer orSplit = new StringTokenizer(condition, "&");
            while (orSplit.hasMoreTokens()) {
                if (calcRule(orSplit.nextToken(), formatModel, cell) == null)
                    return null;
            }
            return interpretRuleCell(formatModel, cell);
        }
        boolean greater = false;
        boolean equal = false;
        boolean less = false;
        if (condition.length() < 2) {
            return null;
        }
        int firstChar = condition.charAt(0);
        switch (firstChar) {
            case '<':
                less = true;
                break;
            case '=':
                equal = true;
                break;
            case '>':
                greater = true;
                break;
        }
        if (!greater && !equal && !less) {
            return null;
        }
        int condLen = 1;
        int secondChar = condition.charAt(1);
        switch (secondChar) {
            case '<':
                less = true;
                condLen = 2;
                break;
            case '=':
                equal = true;
                condLen = 2;
                break;
            case '>':
                greater = true;
                condLen = 2;
                break;
        }
        String compareItem = condition.substring(condLen);
        firstChar = compareItem.charAt(0);
        Double diff;
        try {
            if (firstChar == '"' || firstChar == 8189) {//Excel seems to store " as char(8189) ????
                diff = (double) cell.getStringCellValue().compareToIgnoreCase(compareItem.substring(1, compareItem.length()-1));
                //compare strings
            } else {
                double compareNumber = 0.0;

                if (firstChar >= '0' && firstChar <= '9') {
                    compareNumber = Double.parseDouble(compareItem);//compare numbers
                } else {
                    Cell compareCell = interpretRuleCell(condition.substring(condLen), cell);
                    if (compareCell != null) {
                        compareNumber = compareCell.getNumericCellValue();

                    }
                }

                diff = cell.getNumericCellValue() - compareNumber;
            }
            if ((greater && diff > 0) || (equal && diff == 0) || (less && diff < 0)) {
               return interpretRuleCell(formatModel, cell);
            }
        } catch (Exception e) {
            //if the cells are not numeric, let it pass
        }
        return null;
    }

    void printFile(String filename){

         // First, copy the base css
        BufferedReader in = null;
        try {
            in = new BufferedReader(new InputStreamReader(
                    getClass().getResourceAsStream(filename)));
            String line;
            while ((line = in.readLine()) != null) {
                head.append(line + "\n");
            }
        } catch (IOException e) {
            throw new IllegalStateException("Reading standard css", e);
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    //noinspection ThrowFromFinallyBlock
                    throw new IllegalStateException("Reading standard css", e);
                }
            }
        }

      }
}