package com.azquo.view;

import com.aspose.cells.*;
import com.aspose.cells.Color;
import com.aspose.cells.Font;
import com.azquo.admindao.UserChoiceDAO;
import com.azquo.adminentities.UserChoice;
import com.azquo.memorydb.*;
import com.azquo.memorydb.Name;
import com.azquo.service.*;
import com.csvreader.CsvWriter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang.math.NumberUtils;
import org.apache.log4j.Logger;
import org.apache.velocity.VelocityContext;

import javax.servlet.http.HttpServletResponse;
import java.awt.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;

/**
 * Created by bill on 29/04/14
 */

public class AzquoBook {

    public static final String cr = "";//removing unnecessary carriage returns


    private static final Logger logger = Logger.getLogger(AzquoBook.class);
    private ValueService valueService;
    private ImportService importService;
    private AdminService adminService;
    private NameService nameService;
    private OnlineService onlineService;
    private UserChoiceDAO userChoiceDAO;
    private SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");
    private SimpleDateFormat ukdf = new SimpleDateFormat("dd/MM/yy");

    public static final String azDataRegion = "az_dataregion";
    public static final String azInput = "az_input";
    public static final String azNext = "az_next";
    public static final String OPTIONPREFIX = "!";

    private static final ObjectMapper jacksonMapper = new ObjectMapper();

    static class RegionInfo {
        String region;
        int row;
        int col;
    }

    private Map<String, String> sortChoices = new HashMap<String, String>();
    private Workbook wb = null;
    private Worksheet azquoSheet = null;
    private Cells azquoCells;
    private List<List<String>> heldValues = new ArrayList<List<String>>();
    int maxWidth;
    public String dataRegionPrefix = null;
    // edd commenting, will be deleted later if not used
    //public String nameChosenJson = null;  // used only for passing the parameter when admin/inspection chooses a name
    int topCell = -1;
    int leftCell = -1;
    List<Integer> colWidth = new ArrayList<Integer>();
    // now add css for each used style
    Map<String, String> shortStyles = new HashMap<String, String>();
    Map<Style, Style> highlightStyles = new HashMap<Style, Style>();
    Map<Range, String> chosenMap = null;
    Map<Range, String> choiceMap = null;
    Map<Cell, CellArea> mergedCells = null;
    Map<String, String> sheetOptions = new HashMap<String, String>();
    //HSSFPalette Colors = null;

    private static final Map<Short, String> ALIGN = new HashMap<Short, String>() {
        {
            put((short) TextAlignmentType.LEFT, "left");
            put((short) TextAlignmentType.CENTER, "center");
            put((short) TextAlignmentType.RIGHT, "right");
            put((short) TextAlignmentType.FILL, "left");
            put((short) TextAlignmentType.JUSTIFY, "left");
            put((short) TextAlignmentType.CENTER, "center");
        }
    };

    private static final Map<Short, String> VERTICAL_ALIGN = new HashMap<Short, String>() {
        {
            put((short) TextAlignmentType.BOTTOM, "bottom");
            put((short) TextAlignmentType.CENTER, "middle");
            put((short) TextAlignmentType.TOP, "top");
        }
    };

    // below adjusted dashed to solid
    private static final Map<Short, String> BORDER = new HashMap<Short, String>() {
        {
            put((short) CellBorderType.DASH_DOT, "solid 1pt");
            put((short) CellBorderType.DASH_DOT_DOT, "solid 1pt");
            put((short) CellBorderType.DASHED, "solid 1pt");
            put((short) CellBorderType.DOTTED, "solid 1pt");
            put((short) CellBorderType.DOUBLE, "double 3pt");
            put((short) CellBorderType.HAIR, "solid 1px");
            put((short) CellBorderType.MEDIUM, "solid 2pt");
            put((short) CellBorderType.MEDIUM_DASH_DOT, "dashed 2pt");
            put((short) CellBorderType.MEDIUM_DASH_DOT_DOT, "solid 2pt");
            put((short) CellBorderType.MEDIUM_DASHED, "solid 2pt");
            put((short) CellBorderType.NONE, "1px solid silver");
            put((short) CellBorderType.SLANTED_DASH_DOT, "solid 2pt");
            put((short) CellBorderType.THICK, "solid 3pt");
            put((short) CellBorderType.THIN, "solid 1pt");
        }
    };

    int maxCol = 0;
    int maxHeight = 0;


    private Formatter sOut;
    Map<Range, String> sortableHeadings = new HashMap<Range, String>();
    Map<String, String[]> givenHeadings = new HashMap<String, String[]>();

//    private static final int ROWSCALE = 16;
//    private static final int COLUMNSCALE = 40;

    private int highlightDays = 0;

    @SuppressWarnings({"unchecked"})

    private static final Map<String, String> SHORTSTYLES = new HashMap<String, String>() {
        {
            put("background-color", "bc");
            put("border-bottom", "bb");
            put("border-bottom-color", "bbc");
            put("border-right", "br");
            put("border-right-color", "brc");
            put("color", "c");
            put("font-size", "fs");
            put("font-weight", "fw");
            put("height", "h");
            put("left", "l");
            put("position", "p");
            put("text-align", "ta");
            put("top", "t");
            put("vertical-align", "va");
            put("width", "w");
            put("z-index", "z");
        }
    };

    public AzquoBook(final ValueService valueService, AdminService adminService, NameService nameService, ImportService importService, UserChoiceDAO userChoiceDAO, OnlineService onlineService) throws Exception {
        this.valueService = valueService;
        this.adminService = adminService;
        this.userChoiceDAO = userChoiceDAO;
        this.nameService = nameService;
        this.importService = importService;
        this.onlineService = onlineService;
    }


    public int getMaxHeight() {
        return maxHeight;
    }

    public int getMaxWidth() {
        return maxWidth;
    }

    public int getTopCell() {
        return topCell;
    }

    public int getLeftCell() {
        return leftCell;
    }

    public int getMaxRow() {
        return azquoCells.getMaxRow();
    }

    public int getMaxCol() {
        return maxCol;
    }


    public void setSheet(int sheetNo) {
        azquoSheet = wb.getWorksheets().get(sheetNo);
        azquoCells = azquoSheet.getCells();
        createMergeMap();

    }

    private void createMergeMap() {
        mergedCells = new HashMap<Cell, CellArea>();
        for (Object o : azquoCells.getMergedCells()) { // can't seem to get a typed list
            CellArea r = (CellArea) o;
            mergedCells.put(azquoCells.get(r.StartRow, r.StartColumn), r);
        }
    }

     private Range getRange(String rangeName) {
        for (int i = 0; i < wb.getWorksheets().getNames().getCount(); i++) {
            com.aspose.cells.Name name = wb.getWorksheets().getNames().get(i);
            if (name.getText().equalsIgnoreCase(rangeName)) {
                return name.getRange();

            }
        }
        return null;
    }


    public String getRangeValue(String rangeName) {
        Range range = getRange(rangeName);
        if (range != null) {
            return range.getCellOrNull(0, 0).getStringValue();
        }
        return null;
    }

/*    private Range interpretRangeName(String cellString) {
        return getRangeTest(cellString);
    }*/

    private void createChosenMap() {
        chosenMap = new HashMap<Range, String>();
        for (int i = 0; i < wb.getWorksheets().getNames().getCount(); i++) {
            com.aspose.cells.Name name = wb.getWorksheets().getNames().get(i);
            if (name.getText().toLowerCase().endsWith("chosen") && name.getRange().getWorksheet() == azquoSheet) {
                Range range = name.getRange();
                // if range was null it would have null pointered by now!
                chosenMap.put(range, name.getText().substring(0, name.getText().length() - 6).toLowerCase());
            }
        }
    }

    private void createChoiceMap() {
        choiceMap = new HashMap<Range, String>();
        for (int i = 0; i < wb.getWorksheets().getNames().getCount(); i++) {
            com.aspose.cells.Name name = wb.getWorksheets().getNames().get(i);
            if (name.getText().toLowerCase().endsWith("choice") && name.getRange().getWorksheet() == azquoSheet) {
                Range range = name.getRange();
                // if range was null it would have null pointered by now!
                choiceMap.put(range, name.getText().substring(0, name.getText().length() - 6).toLowerCase());
            }
        }
    }


    public void calculateAll() {

        wb.calculateFormula();

    }

    private String rangeToText(Range range) {
        return rangeToText(range, false).toString();
    }


    private void setupColwidths() {
        maxWidth = 0;
        colWidth = new ArrayList<Integer>();
        for (int c = 0; c <= maxCol; c++) {
            int colW = azquoCells.getColumnWidthPixel(c);
            shortStyles.put("left:" + maxWidth + "px;width:" + colW + "px", "cp" + c);
            colWidth.add(colW);
            maxWidth += colW + 1;
        }

    }

    private void setupMaxHeight() {
        maxHeight = 0;
        for (int rowNo = 0; rowNo < azquoCells.getMaxRow(); rowNo++) {
            maxHeight += azquoCells.getRowHeightPixel(rowNo);
        }
    }

    private void insertRows(Range range, int rowCount) {
        int existingRows = range.getRowCount();
        if (existingRows < rowCount) {
            int copyRow = range.getFirstRow() + existingRows - 2;
            azquoCells.insertRows(copyRow + 1, rowCount - existingRows);
            for (int rowNo = copyRow + 1; rowNo < range.getFirstRow() + rowCount - 1; rowNo++) {
                azquoCells.copyRow(azquoCells, copyRow, rowNo);
            }
            calculateAll();
        }
    }


    private void insertCols(Range range, int colCount) {
        int existingCols = range.getColumnCount();
        if (existingCols < colCount) {
            int copyCol = range.getFirstColumn() + existingCols - 2;
            azquoCells.insertColumns(copyCol + 1, colCount - existingCols);
            for (int colNo = copyCol + 1; colNo < range.getFirstColumn() + colCount - 1; colNo++) {

                azquoCells.copyColumn(azquoCells, copyCol, colNo);
            }

            calculateAll();

        }
    }


    private void setCellValue(Cell currentCell, String val) {
        if (currentCell.getFormula() != null) {
            return;
        }
        if (val.length() == 0) {
            currentCell.setValue(null);
            return;
        }
        if (val.endsWith("%") && NumberUtils.isNumber(val.substring(0, val.length() - 1))) {
            currentCell.setValue(Double.parseDouble(val.substring(0, val.length() - 1)) / 100);
        } else {
            if (val.startsWith("$") || val.startsWith("£")) {//remove £ or $ prefix - what about other currencies???
                val = val.substring(1);
            }
            try {
                Double d = Double.parseDouble(val);
                currentCell.setValue(d);
            } catch (Exception e) {
                currentCell.setValue(val);
            }
        }
    }

    private void setHighlights(LoggedInConnection loggedInConnection, Map<Cell, Boolean> highlighted) {
        //NOTE - This may change the formatting of model cells for conditional formats, which may make a mess!

        for (int i = 0; i < wb.getWorksheets().getNames().getCount(); i++) {
            com.aspose.cells.Name name = wb.getWorksheets().getNames().get(i);
            if (name.getText().toLowerCase().startsWith(dataRegionPrefix) && name.getRange().getWorksheet() == azquoSheet) {
                Range range = name.getRange();
                String region = name.getText().substring(dataRegionPrefix.length());
                // there was a if range is null return but that would have null pointered by now . . .
                for (int rowNo = 0; rowNo < range.getRowCount(); rowNo++) {
                    for (int colNo = 0; colNo < range.getColumnCount(); colNo++) {
                        Cell cell = range.getCellOrNull(rowNo, colNo);
                        if (cell != null && cell.getValue() != null) {

                            if (highlightDays >= valueService.getAge(loggedInConnection, region, rowNo, colNo)) {
                                cell.setStyle(highlightStyle(cell));
                                highlighted.put(cell, true);
                            }
                        }
                    }
                }
            }
        }
    }


    private String[] splitRange(String fillText) {
        String[] rows = fillText.split("\n", -1);
        String row = rows[rows.length - 1];
        return  row.split("\t", -1);
     }


    private void fillRange(String regionName, String fillText, String lockMap, boolean overwrite) {
        // for the headings, the lockmap is "locked" rather than the full array.
        boolean shapeAdjusted = false;
        Range range = getRange(regionName);
        if (range == null) return;
        String[] rows = fillText.split("\n", -1);
        String[] rowLocks = lockMap.split("\n", -1);
        for (int rowNo = 0; rowNo < rows.length; rowNo++) {
            String row = rows[rowNo];
            String rowLock = rowLocks[0];
            if (rowNo < rowLocks.length) {
                rowLock = rowLocks[rowNo];
            }
            String[] vals = row.split("\t", -1);
            String[] locks = rowLock.split("\t", -1);
            int colCount = vals.length;
            if (!shapeAdjusted) {
                int rowCount = rows.length;
                insertRows(range, rowCount);
                insertCols(range, colCount);
                range = getRange(regionName);
                createMergeMap();
                shapeAdjusted = true;
            }
            for (int col = 0; col < vals.length; col++) {
                String val = vals[col];
                String locked = locks[0];
                if (col < locks.length) {
                    locked = locks[col];
                }
                if (val.equals("0.0")) val = "";
                Cell currentCell = azquoCells.get(range.getFirstRow() + rowNo, range.getFirstColumn() + col);
                if (locked.equals("LOCKED")) {
                    currentCell.getStyle().setLocked(true);
                }
                String existingCellVal = currentCell.getStringValue();
                if (overwrite || existingCellVal==null || existingCellVal.length()==0){
                    setCellValue(currentCell, val);
                }
            }
        }
    }

/*    private String colToLetters(int column) {
        if (column < 27) {
            char c = (char) (column + 64);
            return c + "";
        }
        char firstChar = 64;
        while (column > 26) {
            firstChar++;
            column -= 26;
        }
        return firstChar + "" + (char) (column + 64);
    }*/

/*    private String rangeToString(Range range) {
        return (colToLetters(range.getFirstColumn() + 1) + (range.getFirstRow() + 1) + ":" + colToLetters(range.getFirstColumn() + range.getColumnCount()) + (range.getFirstRow() + range.getRowCount()));
    }*/

    private String fillRegion(LoggedInConnection loggedInConnection, String region) throws Exception {
        logger.info("loading " + region);
        int filterCount = optionNumber(region, "hiderows");
        if (filterCount == 0)
            filterCount = -1;//we are going to ignore the row headings returned on the first call, but use this flag to get them on the second.
        int maxRows = optionNumber(region, "maxrows");
        //if (maxRows == 0) loggedInConnection.clearSortCols();//clear it.  not sure why this is done - pushes the sort from the load into the spreadsheet if there is no restriction on si
        int maxCols = optionNumber(region, "maxcols");

        Range rowHeadings = getRange("az_rowheadings" + region);
        if (rowHeadings == null) {
            //assume this is an import region
            return "";
            //return "error: no range az_RowHeadings" + region;
        }
        String headings = rangeToText(rowHeadings);
        if (headings.startsWith("error:")) {
            return headings;
        }
        String result = valueService.setupRowHeadings(loggedInConnection, region, headings);
        if (result.startsWith("error:")) {
            return result;
        }
        //don't bother to display yet - maybe need to filter out or sort
        Range columnHeadings = getRange("az_columnheadings" + region);
        if (columnHeadings == null) {
            return "error: no range az_ColumnHeadings" + region;
        }
        headings = rangeToText(columnHeadings);
        if (headings.startsWith("error:")) {
            return headings;
        }
        result = valueService.setupColumnHeadings(loggedInConnection, region, headings);
        if (result.startsWith("error:")) return result;
        //fillRange("az_displaycolumnheadings" + region, result, "LOCKED");
        Range context = getRange("az_context" + region);
        if (context == null) {
            return "error: no range az_Context" + region;
        }
        headings = rangeToText(context);
        if (headings.startsWith("error:")) {
            return headings;
        }
        try {
            result = valueService.getDataRegion(loggedInConnection, headings, region, filterCount, maxRows, maxCols);
            if (result.startsWith("error:")) return result;
            String language = Name.DEFAULT_DISPLAY_NAME;//TODO  Find the language!
            fillRange(dataRegionPrefix + region, result, loggedInConnection.getLockMap(region), true);
            result = valueService.getColumnHeadings(loggedInConnection, region, language);
            fillRange("az_displaycolumnheadings" + region, result, "LOCKED", false);
            String sortable = hasOption(region, "sortable");
            if (sortable != null) {
                Range displayColumnHeadings = getRange("az_displaycolumnheadings" + region);
                if (displayColumnHeadings != null) {
                    givenHeadings.put("columns:" + region,splitRange(result));

                }
            }

            result = valueService.getRowHeadings(loggedInConnection, region, language, filterCount);
            fillRange("az_displayrowheadings" + region, result, "LOCKED", false);
            if (sortable!=null && sortable.equalsIgnoreCase("all")) {
                Range displayRowHeadings = getRange("az_displayrowheadings" + region);
                if (displayRowHeadings != null) {

                    givenHeadings.put("rows:" + region,splitRange(result));

                }
            }

            //then percentage, sort, trimrowheadings, trimcolumnheadings, setchartdata
            String chartName = hasOption(region, "chart");
            if (chartName != null) {
                for (int chartNo = 0; chartNo < azquoSheet.getCharts().getCount(); chartNo++) {
                    Chart chart = azquoSheet.getCharts().get(chartNo);
                    if (chart.getName().equalsIgnoreCase(chartName)) {
                        boolean isVertical = chart.getNSeries().get(0).isVerticalValues();
                        Range displayColumnHeadings = getRange("az_displaycolumnheadings" + region);
                        Range displayRowHeadings = getRange("az_displayrowheadings" + region);
                        chart.getNSeries().clear();
                        chart.getNSeries().addR1C1("R[" + (displayColumnHeadings.getFirstRow()) + "]C["
                                + (displayRowHeadings.getFirstColumn()) + "]:R["
                                + (displayRowHeadings.getFirstRow() + displayRowHeadings.getRowCount() - 1) + "]C["
                                + (displayColumnHeadings.getFirstColumn() + displayColumnHeadings.getColumnCount() - 1) + "]", isVertical);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return e.getMessage();
        }
        return "";
    }


    private Style highlightStyle(Cell cell) {
        return highlightStyle(cell.getStyle());
    }

    private Style highlightStyle(Style origStyle) {
        Style highlight = highlightStyles.get(origStyle);
        if (highlight == null) {
            highlight = wb.createStyle();
            highlight.copy(origStyle);
            highlight.getFont().setColor(Color.getRed());
            highlightStyles.put(origStyle, highlight);
        }
        return highlight;

    }

    public int optionNumber(String region, String optionName) {
        String option = sheetOptions.get(OPTIONPREFIX + optionName + region);
        if (option == null) {
            option = hasOption(region, optionName);

        }
        if (option == null) return 0;
        int optionValue = 0;
        // I wonder should we be doing isnumber on this type of thing?
        try {
            optionValue = Integer.parseInt(option);
        } catch (Exception ignored) {
        }
        return optionValue;
    }

    public String hasOption(String region, String optionName) {
        String options = sheetOptions.get(OPTIONPREFIX + optionName + region);
        if (options != null) {
            if (options.length() == 0) {
                return null;
            }
            return options;
        }

        Range optionrange = getRange("az_options" + region);
        if (optionrange == null || optionrange.getCellOrNull(0, 0) == null)
            return null;
        options = optionrange.getCellOrNull(0, 0).getStringValue();
        int foundPos = options.toLowerCase().indexOf(optionName.toLowerCase());
        if (foundPos < 0) {
            return null;
        }
        if (options.length() > foundPos + optionName.length()) {
            options = options.substring(foundPos + optionName.length());
            char operator = options.charAt(0);
            if (operator=='>' ) {//interpret the '>' symbol as '-' to create an integer
                options = "-" + options.substring(1);
            }else{
                //ignore '=' or a space
                options = options.substring(1);
            }
            foundPos = options.indexOf(",");
            if (foundPos > 0) {
                options = options.substring(0, foundPos);
            }
            return options;
        }
        return "";
    }

    private List<String> interpretList(String list) {
        //expects a list  "aaaa","bbbb"  etc, though it will pay attention only to the quotes
        List<String> items = new ArrayList<String>();
        StringBuffer item = new StringBuffer();
        boolean inItem = false;
        int pos = 0;
        while (pos < list.length()) {
            char c = list.charAt(pos++);
            if (c == '\"' || c == '“' || c == '”') {
                if (inItem) {
                    inItem = false;
                    items.add(item.toString());
                    item = new StringBuffer();
                } else {
                    inItem = true;
                }
            } else {
                if (inItem) item.append(c);
            }
        }
        return items;

    }

    private String addOption(String item, String selected) {
        String content;
        if (item.contains("'")){
            content = "<option value = \"" + item + "\"";
        }else{
            content = "<option value = '" + item + "'";
        }
        if (item.toLowerCase().equals(selected.toLowerCase())) {
            content += " selected";
        }
        content += ">" + item + "</option>" + cr;
        return content;
    }

    private void calcMaxCol() {

        maxCol = azquoCells.getMaxColumn();

    }


    private void addStyle(String styleName, String styleValue) {
        styleName = styleName.toLowerCase();
        styleValue = styleValue.toLowerCase();
        String fullstyle = styleName + ":" + styleValue;
        String shortStyle = shortStyles.get(fullstyle);
        if (shortStyle == null) {

            String shortName = SHORTSTYLES.get(styleName);
            if (shortName == null) {
                shortName = styleName;
            }
            shortStyle = shortName + styleValue.replace(" ", "").replace("#", "");
            shortStyles.put(fullstyle, shortStyle);
        }
        sOut.format(shortStyle + " ");


    }

/*    private String hex2(short i) {
        if (i < 16) {
            return "0" + Integer.toHexString(i);
        } else {
            return Integer.toHexString(i);
        }
    }*/

    private void StyleColor(String attr, Color color) {
        int rgb = color.toArgb();
        addStyle(attr, String.format("#%1X", rgb & 0xFFFFFF));
    }

    private void fontStyle(Style style) {
        Font font = style.getFont();
        if (font.isBold()) {
            addStyle("font-weight", "bold");
        }
        if (font.isItalic()) {
            addStyle("font-style", "italic");
        }
        int fontheight = font.getSize();
        fontheight = fontheight * 4 / 3;

       // if (fontheight == 9) {
       //     //fix for stupid ol Windows
        //    fontheight = 10;
       // }
        addStyle("font-size", fontheight + "px");
        // Font color is handled with the other colors
    }

    private Style getStyle(int rowNo, int colNo) {
        Style style;
        Cell cell = azquoCells.get(rowNo, colNo);
        if (cell == null) {
            // changed from getRow to getRows().get
            style = azquoCells.getRows().get(rowNo).getStyle();
            if (style == null) {
                Style colStyle = azquoCells.getColumns().get(colNo).getStyle();
                if (colStyle != null) {
                    style = colStyle;
                }
            }
            if (style == null) {
                style = null;//there should be a style somewhere??
            }
        } else {
            style = cell.getStyle();
        }
        return style;
    }

    private void setCellClass(int rowNo, int colNo, Cell cell, boolean highlighted) {
        Style style = getStyle(rowNo, colNo);
        int alignment = style.getHorizontalAlignment();
        if (cell != null && cell.getType() == CellValueType.IS_STRING && alignment == TextAlignmentType.GENERAL) {
            alignment = TextAlignmentType.LEFT; //Excel defaults text alignment left, numbers to the right
        }
        styleOut("text-align", (short) alignment, ALIGN);
        styleOut("vertical-align", (short) style.getVerticalAlignment(), VERTICAL_ALIGN);
        fontStyle(style);
        Border borderRight = style.getBorders().getByBorderType(BorderType.RIGHT_BORDER);
        Border borderBottom = style.getBorders().getByBorderType(BorderType.BOTTOM_BORDER);
        if (borderBottom == null) {

            Style nextStyle = getStyle(rowNo + 1, colNo);
            borderBottom = nextStyle.getBorders().getByBorderType(BorderType.TOP_BORDER);
        }
        if (borderRight == null) {
            Style nextStyle = getStyle(rowNo, colNo + 1);
            borderRight = nextStyle.getBorders().getByBorderType(BorderType.LEFT_BORDER);
        }
        if (borderRight != null) {//this is the 'blank' color
            styleOut("border-right", (short) borderRight.getLineStyle(), BORDER);
        }
        if (borderBottom != null) {//this is the 'blank' color
            styleOut("border-bottom", (short) borderBottom.getLineStyle(), BORDER);
        }
        if (style.isTextWrapped()) {
            addStyle("word-wrap", "normal");
        }
        Color color = style.getForegroundColor();
        if (cell != null) {
            ConditionalFormattingResult cfr1 = cell.getConditionalFormattingResult();
            if (cfr1 != null) {
                if (cfr1.getConditionalStyle() != null) {
                    style = cfr1.getConditionalStyle();
                    if (highlighted) {
                        style = highlightStyle(style);
                    }
                    color = style.getForegroundColor();
                }
            }
        }
        StyleColor("background-color", color);
        StyleColor("color", style.getFont().getColor());
        StyleColor("border-right-color", style.getBorders().getByBorderType(BorderType.RIGHT_BORDER).getColor());
        StyleColor("border-bottom-color", style.getBorders().getByBorderType(BorderType.BOTTOM_BORDER).getColor());
    }

    private <K> void styleOut(String attr, K key, Map<K, String> mapping) {
        String value = mapping.get(key);
        if (value != null) {
            addStyle(attr, value);
        }
    }

/*    private String alignStyle(Cell cell, Style style) {
        if (style.getHorizontalAlignment() == TextAlignmentType.GENERAL) {
            switch (cell.getType()) {
                case CellValueType.IS_STRING:
                    addStyle("text-align", "left");
                case CellValueType.IS_BOOL:
                case CellValueType.IS_ERROR:
                    addStyle("text-align", "center");
                case CellValueType.IS_NUMERIC:
                default:
                    // "right" is the default
                    break;
            }
        }
        return "";
    }*/

    private String loadAdminData(LoggedInConnection loggedInConnection) throws Exception {
        for (int i = 0; i < wb.getWorksheets().getNames().getCount(); i++) {
            com.aspose.cells.Name name = wb.getWorksheets().getNames().get(i);
            if (name.getText().toLowerCase().startsWith("az_headings") && name.getRange().getWorksheet() == azquoSheet) {
                String regionName = name.getText().substring(11).toLowerCase();
                String error = fillAdminData(loggedInConnection, regionName);
                if (error.startsWith("error:")) {
                    return error;
                }
            }
        }
        return "";

    }

    public String jsonError(String data, int pos) {
        int end = data.length();
        if (pos < end - 30) {
            end = pos + 30;
        }
        return ("error: json parsing problem at " + data.substring(pos, end));
    }

    public String readJsonData(String data, Map<String, String> pairs) {
        //should use Jackson for this,....
        /*
        final String json = "{}";
       final ObjectMapper mapper = new ObjectMapper();
       final MapType type = mapper.getTypeFactory().constructMapType(
       Map.class, String.class, Object.class);
       final Map<String, Object> data = mapper.readValue(json, type);


        */
        int pos = 1;
        if (data.charAt(pos++) != '{') return jsonError(data, pos);
        while (data.charAt(pos++) == '"') {
            int endName = data.indexOf("\"", pos);
            if (endName < 0) return jsonError(data, pos);
            String jsonName = data.substring(pos, endName);
            pos = endName + 1;
            if (data.charAt(pos++) != ':') return jsonError(data, pos);
            String jsonValue;
            if (data.charAt(pos) != '"') {
                int endLine = data.indexOf("}", pos);
                endName = data.indexOf(",", pos);
                if (endName < 0 || (endName > 0 && endLine < endName)) endName = endLine;
                if (endName < 0) return jsonError(data, pos);
                jsonValue = data.substring(pos, endName);
                pos = endName;
            } else {
                pos++;
                endName = data.indexOf("\"", pos);
                if (endName < 0) return jsonError(data, pos);
                jsonValue = data.substring(pos, endName);
                pos = endName + 1;
            }
            pairs.put(jsonName, jsonValue);
            if (data.charAt(pos) == '}') {
                break;
            }
            if (data.charAt(pos++) != ',') return jsonError(data, pos);
        }
        return data.substring(pos + 1);


    }

    private String evaluateExpression(String expression, Map<String, String> pairs) {
        //evaluates simple text expressions
        StringBuilder sb = new StringBuilder();
        int pos = 0;
        while (pos < expression.length()) {
            if (expression.charAt(pos) == '"') {
                int endQuote = expression.indexOf("\"", ++pos);
                if (endQuote < 0) return "error: expression not understood " + expression;
                sb.append(expression.substring(pos, endQuote));
                pos = endQuote + 1;
            } else if (expression.charAt(pos) == '&' || expression.charAt(pos) == ' ') {
                pos++;
            } else {
                int endTerm = (expression + "&").indexOf("&", pos);
                String val = pairs.get(expression.substring(pos, endTerm).trim());
                if (val == null) return "error: expression not understood " + expression;
                sb.append(val);
                pos = endTerm;
            }
        }
        return sb.toString();
    }

    public String fillAdminData(LoggedInConnection loggedInConnection, String regionName) throws Exception {
        String data = null;
        Range headingsRange = getRange("az_headings" + regionName);
        int headingsRow = headingsRange.getFirstRow();
        int headingsCol = headingsRange.getFirstColumn();
        int firstHeading = headingsRange.getFirstColumn();
        if (regionName.equals("data") && loggedInConnection.getNamesToSearch() != null) {
            Map<Set<com.azquo.memorydb.Name>, Set<Value>> shownValues = valueService.getSearchValues(loggedInConnection.getNamesToSearch());
            loggedInConnection.setValuesFound(shownValues);
            LinkedHashSet<com.azquo.memorydb.Name> nameHeadings = valueService.getHeadings(shownValues);
            int colNo = firstHeading + 1;
            int rowNo = 0;
            for (com.azquo.memorydb.Name name : nameHeadings) {
                azquoCells.get(headingsRow, headingsCol + colNo).setValue(name.getDefaultDisplayName());
                colNo++;
            }
            for (Set<com.azquo.memorydb.Name> names : shownValues.keySet()) {
                rowNo++;
                colNo = firstHeading;
                azquoCells.get(headingsRow + rowNo, headingsCol + colNo).setValue(valueService.addValues(shownValues.get(names)));
                colNo++;
                for (com.azquo.memorydb.Name name : nameHeadings) {
                    for (com.azquo.memorydb.Name valueName : names) {
                        if (valueName.findAllParents().contains(name)) {
                            azquoCells.get(headingsRow + rowNo, headingsCol + colNo).setValue(valueName.getDefaultDisplayName());
                        }
                    }
                    colNo++;
                }
            }
        } else {
            if (regionName.equals("databases")) {
                data = jacksonMapper.writeValueAsString(adminService.getDatabaseListForBusiness(loggedInConnection));
            } else if (regionName.equals("uploads")) {
                data = jacksonMapper.writeValueAsString(adminService.getUploadRecordsForDisplayForBusiness(loggedInConnection));
            } else if (regionName.equals("users")) {
                data = jacksonMapper.writeValueAsString(adminService.getUserListForBusiness(loggedInConnection));
            } else if (regionName.equals("permissions")) {
                data = jacksonMapper.writeValueAsString(adminService.getPermissionList(loggedInConnection));
            } else if (regionName.equals("reports")) {
                data = jacksonMapper.writeValueAsString(adminService.getReportList(loggedInConnection));
            }
            if (data == null) return "";
            int lastHeading = headingsRange.getColumnCount();
            int rowNo = 0;
            //strip the square brackets
            while (data.length() > 2) {
                Map<String, String> pairs = new HashMap<String, String>();
                data = readJsonData(data, pairs);
                if (data.startsWith("error:")) return data;
                rowNo++;
                for (int colNo = firstHeading; colNo < lastHeading; colNo++) {
                    String heading = headingsRange.get(0, colNo).getStringValue();
                    String link = null;
                    String linkStart = null;
                    int nameEnd = heading.indexOf(";");
                    if (nameEnd > 0) {
                        link = heading.substring(nameEnd + 1);
                        heading = heading.substring(0, nameEnd);
                        if (link.startsWith("href=")) {
                            link = link.substring(5);
                            linkStart = "<a href=";
                        } else if (link.startsWith("onclick=")) {
                            link = link.substring(8);
                            linkStart = "<a href='#' onclick=";
                        } else {
                            link = null;
                        }
                    }
                    String valFound = pairs.get(heading);
                    if (link != null) {
                        String linkAddr = evaluateExpression(link.replace("“", "\"").replace("”", "\""), pairs);//excel uses fancy quotes
                        if (linkAddr.startsWith("error:")) return linkAddr;
                        valFound = linkStart + linkAddr + ">" + valFound + "</a>";
                    }
                    if (valFound != null) {
                        azquoCells.get(headingsRow + rowNo, headingsCol + colNo).setStyle(headingsRange.get(0, colNo).getStyle());
                        try {
                            //if it can be parsed as a date, it is a date!
                            SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");
                            Date date = df.parse(valFound);
                            azquoCells.get(headingsRow + rowNo, headingsCol + colNo).setValue(date);
                        } catch (Exception e) {
                            azquoCells.get(headingsRow + rowNo, headingsCol + colNo).setValue(valFound);
                        }
                    }
                }
            }
        }
        return "";
    }

    private String getHTMLTableData(String rangeName) {
        Range range = getRange(rangeName);
        if (range == null) return "";
        StringBuilder sb = new StringBuilder();
        sb.append("<table>" + cr);
        for (int row = 0; row < range.getRowCount(); row++) {
            sb.append("<tr>" + cr);
            for (int col = 0; col < range.getColumnCount(); col++) {
                sb.append("<td>");
                String val = range.get(row, col).getStringValue().replace(";", " ");//this 'replace' should be removed in due course
                if (val == null) val = "";
                sb.append(val).append("</td>");
            }
            sb.append("</tr>" + cr);
        }
        sb.append("</table>" + cr);
        return sb.toString();
    }

    public void fillVelocityOptionInfo(LoggedInConnection loggedInConnection, VelocityContext velocityContext) {
        List<VRegion> vRegions = new ArrayList<VRegion>();
        for (int i = 0; i < wb.getWorksheets().getNames().getCount(); i++) {
            com.aspose.cells.Name name = wb.getWorksheets().getNames().get(i);
            if (name.getText().toLowerCase().startsWith(dataRegionPrefix) && name.getRange().getWorksheet() == azquoSheet) {
                VRegion vRegion = new VRegion();
                String region = name.getText().substring(dataRegionPrefix.length()).toLowerCase();
                vRegion.name = region;
                vRegion.maxrows = optionNumber(region, "maxrows") + "";
                vRegion.maxcols = optionNumber(region, "maxcols") + "";
                vRegion.hiderows = optionNumber(region, "hiderows") + "";
                String sortable = "";
                if (hasOption(region, "sortable") != null) sortable = "checked";
                vRegion.sortable = sortable;
                vRegion.rowdata = getHTMLTableData("az_RowHeadings" + region);
                vRegion.coldata = getHTMLTableData("az_ColumnHeadings" + region);
                vRegion.contextdata = getHTMLTableData("az_Context" + region);
                vRegions.add(vRegion);
            }
        }
        velocityContext.put("hdays", highlightDays + "");
        velocityContext.put("menuregions", vRegions);
        velocityContext.put("regions", getRegions(loggedInConnection, dataRegionPrefix).toString());//this is for javascript routines

    }

    public String loadData(LoggedInConnection loggedInConnection) throws Exception {


        calculateAll();
        for (int i = 0; i < wb.getWorksheets().getNames().getCount(); i++) {
            com.aspose.cells.Name name = wb.getWorksheets().getNames().get(i);
            if (name.getText().toLowerCase().startsWith(dataRegionPrefix) && name.getRange().getWorksheet() == azquoSheet) {
                String regionName = name.getText().substring(dataRegionPrefix.length()).toLowerCase();
                long regStart = System.currentTimeMillis();
                String error = fillRegion(loggedInConnection, regionName);
                System.out.println("fillregion took " + (System.currentTimeMillis() - regStart) + " millisecs");
                regStart = System.currentTimeMillis();
                calculateAll();
                System.out.println("calcall took " + (System.currentTimeMillis() - regStart) + " millisecs");
                if (error.startsWith("error:")) {
                    return error;
                }
            }
        }
          return "";
    }


    public String printAllStyles() {
        StringBuilder sb = new StringBuilder();

        for (String className : shortStyles.keySet()) {
            sb.append(".").append(shortStyles.get(className)).append(" {").append(className).append("}\n");
        }
        return sb.toString();

    }


    private void setChoices(LoggedInConnection loggedInConnection, int reportId) {

        createChosenMap();
        createChoiceMap();//to check when choice cells may be changed
        for (Range range : chosenMap.keySet()) {
            if (range.getRowCount() == 1 && range.getColumnCount() == 1) {
                String choiceName = chosenMap.get(range);
                Range choice = getRange(choiceName + "choice");
                if (choice != null) {
                    UserChoice userChoice = userChoiceDAO.findForUserIdReportIdAndChoice(loggedInConnection.getUser().getId(), reportId, choiceName);
                    if (userChoice != null) {
                        range.setValue(userChoice.getChoiceValue());
                    }
                }
            }
        }
        calculateAll();
        List<UserChoice> allChoices = userChoiceDAO.findForUserIdAndReportId(loggedInConnection.getUser().getId(), reportId);
        for (UserChoice uc : allChoices) {
            String choiceName = uc.getChoiceName();
            if (choiceName.startsWith(OPTIONPREFIX)) {
                sheetOptions.put(choiceName, uc.getChoiceValue());
            }
        }
    }

    private void setSortCols(LoggedInConnection loggedInConnection, int reportId) {
        for (int i = 0; i < wb.getWorksheets().getNames().getCount(); i++) {
            com.aspose.cells.Name name = wb.getWorksheets().getNames().get(i);
            if (name.getText().toLowerCase().startsWith(dataRegionPrefix) && name.getRange().getWorksheet() == azquoSheet) {
                String region = name.getText().substring(dataRegionPrefix.length());
                UserChoice userSortCol = userChoiceDAO.findForUserIdReportIdAndChoice(loggedInConnection.getUser().getId(), reportId, "sort " + region + " by row");
                if (userSortCol != null) {

                    try {
                        String choiceVal = userSortCol.getChoiceValue();
                        sortChoices.put("rows:" + region, choiceVal);
                        loggedInConnection.setSortRow(region, choiceVal);
                    } catch (Exception ignored) {
                        //set by the system, so should not have an exception
                    }
                }
                UserChoice userSortRow = userChoiceDAO.findForUserIdReportIdAndChoice(loggedInConnection.getUser().getId(), reportId, "sort " + region + " by column");
                if (userSortRow != null) {
                    try {
                        sortChoices.put("columns:" + region, userSortRow.getChoiceValue());
                        loggedInConnection.setSortCol(region, userSortRow.getChoiceValue());
                    } catch (Exception ignored) {
                        //set by the system, so should not have an exception
                    }
                }

            }
        }
    }

    public String prepareSheet(LoggedInConnection loggedInConnection, int reportId, Map<Cell, Boolean> highlighted) throws Exception {
        String error;
        calcMaxCol();
        setChoices(loggedInConnection, reportId);
        setSortCols(loggedInConnection, reportId);
        UserChoice highlight = userChoiceDAO.findForUserIdReportIdAndChoice(loggedInConnection.getUser().getId(), reportId, "highlight");
        if (highlight != null) {
            try {
                highlightDays = Integer.parseInt(highlight.getChoiceValue());
            } catch (Exception ignored) {
                //missing the highlight would not be a problem
            }
        }
        if (dataRegionPrefix.equals(azDataRegion)) {
            error = loadData(loggedInConnection);

        } else {
            //admin data only loaded on admin sheets
            error = loadAdminData(loggedInConnection);
            // still ignoring error.....
        }
        if (highlightDays > 0) {
            setHighlights(loggedInConnection, highlighted);
        }
        //find the cells that will be used to make choices...
        calcMaxCol();
        setupColwidths();
        setupMaxHeight();
        return error;
    }


    private String createCellId(Cell cell) {
        return "cell" + cell.getRow() + "-" + cell.getColumn();
    }

    public String getCellContent(int rowNo, int colNo) {
        return getCellContentAsString(azquoCells.get(rowNo, colNo));

    }

    private String getCellContentAsString(Cell cell) {
        String content = "";
        if (cell != null) {
            content = cell.getStringValue();

        }
        return content;
    }

    private StringBuilder createCellClass(int rowNo, int colNo, Cell cell, boolean highlighted) {
        StringBuilder cellClass = new StringBuilder();
        sOut = new Formatter(cellClass);
        setCellClass(rowNo, colNo, cell, highlighted);
        cellClass.append("rp").append(rowNo).append(" cp").append(colNo).append(" ");
        return cellClass;
    }

   /*

    public int getUnsortRow(int rowNo){
        if (unSortRows == null) return rowNo;
        Integer sortRow = unSortRows.get(rowNo);
        if (sortRow == null) return rowNo;
        return sortRow;
    }

    private int getSortRow(int rowNo){
        if (sortRows == null) return rowNo;
        Integer sortRow = sortRows.get(rowNo);
        if (sortRow == null) return rowNo;
        return sortRow;

    }

    private void sortTheRows(){
        sortRows = null;
        unSortRows = null;
        if (sortRegion == null || sortRegion.length()==0) {
            return;
        }
        for (int i = 0;i < wb.getWorksheets().getNames().getCount();i++){
            com.aspose.cells.Name name = wb.getWorksheets().getNames().get(i);
           if (name.getText().toLowerCase().equalsIgnoreCase(dataRegionPrefix + sortRegion)) {
                sortRows = new HashMap<Integer, Integer>();
                unSortRows = new HashMap<Integer, Integer>();
                Range range = name.getRange();
                int colOffset = sortColumn -1;
                if (colOffset < 0) colOffset = -sortColumn -1;
                int rowOffset = sortRow - 1;
                if (rowOffset < 0) rowOffset = - sortRow - 1;
                final int sheetSortCol = range.getFirstColumn() + colOffset;
                List list = new ArrayList();
                for (int rowNo = range.getFirstRow(); rowNo <  range.getFirstRow() + range.getRowCount();rowNo++){
                    list.add(rowNo);
                }
                Collections.sort(list, new Comparator() {

                    public int compare(Object o1, Object o2) {
                        //blanks and errors are always last, regardless of sort order
                        int result = 0;
                        Cell cell1 = azquoCells.get((Integer) o1,sheetSortCol);
                        if (cell1 == null) {
                            return 1;
                        }
                        int cellType1 = cell1.getType();
                        if (cellType1 == CellValueType.IS_NULL || cellType1 == CellValueType.IS_BOOL || cellType1 == CellValueType.IS_ERROR) {
                            return 1;
                        }
                        Cell cell2 = azquoCells.get((Integer) o2,sheetSortCol);
                        if (cell2 == null) {
                            return -1;
                        }
                        int cellType2 = cell2.getType();
                        if (cellType2 == CellValueType.IS_NULL || cellType2 == CellValueType.IS_BOOL || cellType2 == CellValueType.IS_ERROR) {
                            return -1;
                        }
                        switch (cellType1) {
                            case CellValueType.IS_STRING:

                                if (cellType2 == CellValueType.IS_STRING || cell2.isFormula()) {
                                    String string1 = cell1.getStringValue();
                                    String string2 = cell2.getStringValue();
                                    if (string1.length() ==0 && string2.length() > 0) return 1;//blanks always last
                                    if (string2.length() == 0 && string1.length() > 0) return -1;
                                    result = ((Comparable) (cell1.getStringValue()))
                                            .compareTo(cell2.getStringValue());

                                } else {
                                    result = 1;
                                }
                                break;
                            case CellValueType.IS_NUMERIC:
                                if (cellType2 == CellValueType.IS_NUMERIC || cell2.isFormula()) {
                                    result = ((Comparable) (cell1.getValue()))
                                            .compareTo(cell2.getValue());

                                } else {
                                    result = -1;
                                }
                                break;
                        }
                        if (sortColumn < 0) return (-result);
                        return result;
                    }
                });
                for (int j=0;j < list.size();j++) {
                    int origRow = j + range.getFirstRow();
                    int sortRow = (Integer)list.get(j);
                    sortRows.put(origRow, sortRow);
                    unSortRows.put(sortRow,origRow);
                }

            }
        }
    }
    */


    private Range chosenRange(Cell cell) {
        return cellInMap(cell, chosenMap);
    }

    private Range choiceRange(Cell cell) {
        return cellInMap(cell, choiceMap);
    }

    private Range cellInMap(Cell cell, Map<Range, String> map) {
        int row = cell.getRow();
        int col = cell.getColumn();
        for (Range range : map.keySet()) {
            if (range.getFirstRow() <= row && range.getFirstRow() + range.getRowCount() > row
                    && range.getFirstColumn() <= col && range.getFirstColumn() + range.getColumnCount() > col) {
                return range;
            }
        }
        return null;
    }

    int choiceOffset(Cell cell) {
        int row = cell.getRow();
        int col = cell.getColumn();
        for (Range range : chosenMap.keySet()) {
            int rowOffset = row - range.getFirstRow();
            if (range.getFirstColumn() == col && rowOffset >= 0 && rowOffset < range.getRowCount()) {
                if (range.getRowCount() == 1) {
                    return 0;
                }
                return rowOffset + 1;
            }
        }
        return 0;//should never get here!
    }


    private int getCellHeight(Cell cell) {
        int cellHeight = azquoCells.getRowHeightPixel(cell.getRow()) - 1;//the reduction is to cater for a border of width 1 px.cell.getRow().rowHeight;
        if (mergedCells.get(cell) != null) {
            CellArea mergeRange = mergedCells.get(cell);
            for (int r = mergeRange.StartRow + 1; r <= mergeRange.EndRow; r++) {
                cellHeight += azquoCells.getRowHeightPixel(r);
            }
        }
        return cellHeight;
    }

    private int getCellWidth(Cell cell) {
        int cellWidth = colWidth.get(cell.getColumn());
        if (mergedCells.get(cell) != null) {
            CellArea mergeRange = mergedCells.get(cell);
            for (int c = mergeRange.StartColumn + 1; c <= mergeRange.EndColumn; c++) {
                cellWidth += azquoCells.getColumnWidthPixel(c);
            }
        }
        return cellWidth;
    }

    private String createCellContentHTML(LoggedInConnection loggedInConnection, Cell cell, String cellClass, String overrideValue) {
        String content = getCellContentAsString(cell);
        // changed to rotation angle from rotation as depreciated, I assume the same value
        if (overrideValue != null){
            content = overrideValue;
        }

        if (cell.getStyle().getRotationAngle() == 90) {
            content = "<div class='r90'>" + content + "</div>";
        }
        Range chosenRange = chosenRange(cell);
        String choiceName = chosenMap.get(chosenRange);
        if (choiceName != null) {
            //create a select list
            StringBuffer selectClass = new StringBuffer();
            selectClass.append("select ");
            sOut = new Formatter(selectClass);
            addStyle("width", getCellWidth(cell) + "px");
            addStyle("height", getCellHeight(cell) + "px");
            StyleColor("background-color", cell.getStyle().getForegroundColor());
            Range choice = getRange(choiceName + "choice");
            if (choice != null) {
                int choiceRow = 0;
                if (choice.getRowCount() > 1) choiceRow = cell.getRow() - chosenRange.getFirstRow();
                List<com.azquo.memorydb.Name> choiceList = new ArrayList<Name>();
                String cellChoice = choice.get(choiceRow, 0).getStringValue();
                List<String> constants = new ArrayList<String>();
                if (cellChoice.startsWith("\"") || cellChoice.startsWith("“")) {
                    constants = interpretList(cellChoice);
                } else {
                    try {
                        System.out.println("SELECTION: choosing from " + cellChoice);
                        choiceList = nameService.parseQuery(loggedInConnection, cellChoice, loggedInConnection.getLanguages());
                    } catch (Exception e) {
                        constants.add(e.getMessage());
                    }
                }
                if (constants.size() > 0 || choiceList.size() > 0) {
                    String origContent = content;
                    String onChange;
                    int choiceOffset = choiceOffset(cell);
                    if (choiceOffset == 0) {
                        onChange = "onchange='selectChosen(\"" + choiceName + "\", true)' id='" + choiceName + "'";
                    } else {
                        choiceName += choiceOffset;
                        onChange = "onchange='selectChosen(\"" + choiceName + "\", false)' id='" + choiceName + "'";
                    }
                    content = "<select class = '" + selectClass + "'" + onChange + " class='" + cellClass + "' >" + cr;
                    //content = "<select class = \"" + selectClass + "\" onchange=\"selectChosen('" + choiceName + "')\" id=\"" + ChosenMap.get(cell) + "\" class=\"" + cellClass + "\" >" + cr;
                    content += "<option value = ''></option>";
                    for (String constant : constants) {
                        content += addOption(constant, origContent);
                    }
                    for (com.azquo.memorydb.Name name : choiceList) {
                        if (name != null) {
                            content += addOption(name.getDefaultDisplayName(), origContent);
                        }
                    }
                    content += "</select>";
                } else {
                    content = "";
                    cell.setValue("");
                }
            }
        }
        return content;

    }


    private void createSortableHeadings(){
        for (String range:givenHeadings.keySet()){
            String type = range.substring(0,range.indexOf(":") - 1);
            String region = range.substring(type.length() + 2);
            Range displayHeadings = getRange("az_display" +  type + "headings" + region);
            sortableHeadings.put(displayHeadings, range);
        }

    }

    public StringBuffer convertToHTML(LoggedInConnection loggedInConnection, Map<Cell, Boolean> highlighted) {

        StringBuffer output = new StringBuffer();
        createSortableHeadings();
        createChosenMap();//the chosen cells may have moved

        int rowNo = 0;
        int shownRowNo = 0;
        int cellTop = 0;
        Row lastRow = null;
        while (rowNo <= azquoCells.getMaxRow()) {
            List<String> rowValues = new ArrayList<String>();
            heldValues.add(rowValues);
            Row row = azquoCells.getRows().get(rowNo);
            if (row != null && !row.isHidden()) {
                shownRowNo++;
                if (topCell == -1) {
                    topCell = rowNo;
                }
                int rowHeight = azquoCells.getRowHeightPixel(rowNo) - 1;//the reduction is to cater for a border of width 1 px.

                shortStyles.put("position:absolute;top:" + cellTop + "px;height:" + rowHeight + "px", "rp" + rowNo);
                for (int colNo = 0; colNo <= maxCol; colNo++) {
                    Cell cell = null;
                    if (row != lastRow) {
                        cell = azquoCells.get(rowNo, colNo);
                    }
                    if (!azquoCells.getColumns().get(colNo).isHidden()) {
                        if (leftCell == -1) {
                            leftCell = colNo;
                        }
                        String content = getCellContentAsString(cell);
//                        String origContent = content.replace(" ", "");
                        String origContent;
                        rowValues.add(content);//saved for when cells are changed
/*                        if (cell.getStyle().getRotationAngle() == 90) {
                            content = "<div class='r90'>" + content + "</div>";
                        }*/
                        Boolean cellHighlighted = highlighted.get(cell);
                        if (cellHighlighted == null) cellHighlighted = false;

                        StringBuilder cellClass = createCellClass(rowNo, colNo, cell, cellHighlighted);
                        String overrideValue = null;
                        if (shownRowNo > 500){
                            overrideValue = "<br/><b>Spreadsheet truncated.  To see full report, download as XLSX</b>";
                        }

                        content = createCellContentHTML(loggedInConnection, cell, cellClass.toString(), overrideValue);
                        String sizeInfo = "";
                        if (mergedCells.get(cell) != null) {
                            addStyle("z-index", "1");
                            //needs a background color to blot out cells underneath (probably we should not show the cells underneath)
                            addStyle("background-color", "white");
                            sizeInfo = " style='height:" + getCellHeight(cell) + "px;width:" + getCellWidth(cell) + "px;'";
                        }


                        Range headingsRange = cellInMap(cell, sortableHeadings);
                        if (headingsRange != null && content.length() > 0) {
                            String headingsRegion = sortableHeadings.get(headingsRange);
                            String[]givenStrings = givenHeadings.get(headingsRegion);
                            if (headingsRegion.startsWith("columns:")) {
                                origContent = givenStrings[colNo - headingsRange.getFirstColumn()];
                                String sortChoice = sortChoices.get(headingsRegion);


                                headingsRegion = headingsRegion.substring(8);
                                if (!origContent.equals(sortChoice)) {//sort is currently up
                                    content += "<div class='sortup'><a href='#' onclick=\"sortCol('" + headingsRegion.trim() + "','" + origContent + "');\"><img src='/images/sortup.png'></a></div>";

                                } else {
                                    content += "<div class='sortup'><a href='#' onclick=\"sortCol('" + headingsRegion.trim() + "','');\"><img src='/images/sort0.png'></a></div>";

                                }
                                if (!(origContent + "-desc").equals(sortChoice)) {//sort down
                                    content += "<div class='sortdown'><a href='#' onclick=\"sortCol('" + headingsRegion.trim() + "','" + origContent + "-desc');\"><img src='/images/sortdown.png'></a></div>";
                                } else {
                                    content += "<div class='sortdown'><a href='#' onclick=\"sortCol('" + headingsRegion.trim() + "','');\"><img src='/images/sort0.png'></a></div>";

                                }
                            } else {
                                origContent = givenStrings[colNo - headingsRange.getFirstColumn()];
                                String sortChoice = sortChoices.get(headingsRegion);
                                headingsRegion = headingsRegion.substring(5);
                                if (!origContent.equals(sortChoice)) {//sort is currently left

                                    content += "<div class='sortleft'><a href='#' onclick=\"sortRow('" + headingsRegion.trim() + "','" + origContent + "');\"><img src='/images/sortleft.png'></a></div>";

                                } else {
                                    content += "<div class='sortleft'><a href='#' onclick=\"sortRow('" + headingsRegion.trim() + "','');\"><img src='/images/sort.png'></a></div>";

                                }
                                if (!(origContent + "-desc").equals(sortChoice)) {
                                    content += "<div class='sortright'><a href='#' onclick=\"sortRow('" + headingsRegion.trim() + "','" + origContent + "-desc');\"><img src='/images/sortright.png'></a></div>";
                                } else {
                                    content += "<div class='sortright'><a href='#' onclick=\"sortRow('" + headingsRegion.trim() + "','');\"><img src='/images/sort.png'></a></div>";

                                }
                            }
                        }
                        if (content.toLowerCase().startsWith("$button;name=")) {//CURRENTLY THIS ASSUMES THAT THE BUTTON LINK WILL ALWAYS BE $button;name=<name>;op=<link>
                            int nameEnd = content.substring(13).indexOf(";");
                            if (nameEnd > 0) {
                                String buttonTitle = content.substring(13, 13 + nameEnd);
                                if (content.contains("javascript:")) {
                                    content = "<div class='button'><a href='#' onclick='" + content.substring(nameEnd + 25) + "'>" + buttonTitle + "</a></div>";
                                } else {
                                    content = "<div class='button'><a href='#' onclick='buttonPressed(" + rowNo + "," + colNo + ")'>" + buttonTitle + "</a></div>";
                                }
                            }
                        }
                        output.append("   <div class='").append(cellClass).append("' ").append(sizeInfo).append(" id='cell")
                                .append(rowNo).append("-").append(colNo).append("'>").append(content.trim()).append("</div>" + cr);
                        //out.format("    <td class=%s %s>%s</td>%n", styleName(style),
                        //        attrs, content);
                        if (shownRowNo > 500){
                            return output;
                        }
                    } else {
                        rowValues.add(getCellContentAsString(cell));
                    }
                 }
                cellTop += rowHeight + 1;
              }
            rowNo++;
            lastRow = row;
        }
        return output;
    }

    public StringBuilder drawCharts(LoggedInConnection loggedInConnection, String basePath) throws Exception {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < azquoSheet.getCharts().getCount(); i++) {
            Chart chart = azquoSheet.getCharts().get(i);
            String name = chart.getName();
            //File tempchart = File.createTempFile(loggedInConnection.getConnectionId() + name ,".jpg");
            String tempname = (loggedInConnection.getUser().getId() + "." + loggedInConnection.getReportId() + "." + name + ".png").replace(" ", "");
            try {

                ImageOrPrintOptions imageOrPrintOptions = new ImageOrPrintOptions();
                imageOrPrintOptions.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                imageOrPrintOptions.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

                chart.toImage(basePath + tempname, imageOrPrintOptions);
                int topOffset = 0; //not sure why we should need this!
                sb.append("<div id='chart").append(name).append("' style='position:absolute;top:")
                        .append(chart.getChartObject().getY() + topOffset).append("px;left:").append(chart.getChartObject().getX()).append("px;'><img src='/api/Download?image=")
                        .append(tempname).append("'/></div>" + cr);
            } catch (Exception e) {
                sb.append("chart ").append(tempname).append(" not found");
            }
        }
        return sb;
    }

/*    public String makeChartTitle(String region) {
        String title = azquoSheet.getName();
        if (region.length() > 0) {
            title += " " + region;
        }
        for (com.aspose.cells.Name name : (Set<com.aspose.cells.Name>) wb.getWorksheets().getNames()) {
            if (name.getText().toLowerCase().endsWith("chosen") && name.getRange().getWorksheet() == azquoSheet) {
                Range range = name.getRange();
                if (range != null) {
                    title += " " + range.get(0, 0).getStringValue();
                }
            }
        }
        return title;
    }


    public String[] getChartHeadings(String region, String headingName) {
        Range rangeFormula = getRange("az_display" + headingName + "headings" + region);

        if (rangeFormula == null) {
            rangeFormula = getRange("az_chart" + headingName + "headings" + region);
            if (rangeFormula == null) {
                String[] error = {"error: no " + headingName + " headings for " + region};
                return error;
            }
        }
        String rangeText = rangeToText(rangeFormula);

        rangeText = rangeText.replace("\n", "\t");
        return rangeText.split("\t");
    }

    public List<List<Number>> getData(String region) {
        List<List<Number>> data = new ArrayList<List<Number>>();
        Range range = getRange(dataRegionPrefix + region);
        if (range == null) {
            return null;
        }
        for (int row = 0; row < range.getRowCount(); row++) {
            List<Number> rowData = new ArrayList<Number>();
            for (int col = 0; col < range.getColumnCount(); col++) {
                Cell cell = range.get(row, col);
                rowData.add(cell.getDoubleValue());
            }
            data.add(rowData);
        }
        return data;
    }

    public String confirmRegion(String region) {
        //look for a region name if the one given is not the correct one!
        String regionFound = null;
        for (int i = 0; i < wb.getWorksheets().getNames().getCount(); i++) {
            com.aspose.cells.Name name = wb.getWorksheets().getNames().get(i);
            if (name.getText().toLowerCase().startsWith(dataRegionPrefix)) {
                if (regionFound != null) {
                    return region;
                }
                regionFound = name.getText().substring(dataRegionPrefix.length());

            }
        }
        return regionFound;
    }*/

    private StringBuilder jsonValue(String name, int val) {
        return jsonValue(name, val + "", true);
    }

    private StringBuilder jsonValue(String name, String value, boolean withComma) {
        StringBuilder sb = new StringBuilder();
        if (withComma) sb.append(",");
        sb.append("\"").append(name).append("\":\"").append(value).append("\"");
        return sb;
    }

    private StringBuilder jsonRange(String name, String array) {
        StringBuilder sb = new StringBuilder();
        sb.append(",\"").append(name).append("\":[");
        if (array != null) {
            String[] rows = array.split("\n", -1);
            boolean firstRow = true;
            for (String row : rows) {
                String[] cells = row.split("\t", -1);
                boolean firstCol = true;
                if (firstRow) {
                    firstRow = false;
                } else {
                    sb.append(",");

                }
                sb.append("[");
                for (String cell : cells) {
                    if (firstCol) {
                        firstCol = false;
                    } else {
                        sb.append(",");

                    }
                    sb.append("\"").append(cell).append("\"");
                }
                sb.append("]");

            }
        }
        sb.append("]");
        return sb;
    }

    private String unlockRange(Range dataRange) {
        boolean firstRow = true;
        String lockMap = "";
        for (int rowNo = dataRange.getFirstRow(); rowNo < dataRange.getFirstRow() + dataRange.getRowCount(); rowNo++) {
            if (firstRow) {
                firstRow = false;
            } else {
                lockMap += "\n";
            }
            boolean firstCol = true;
            for (int colNo = dataRange.getFirstColumn(); colNo < dataRange.getFirstColumn() + dataRange.getColumnCount(); colNo++) {
                if (firstCol) {
                    firstCol = false;
                } else {
                    lockMap += "\t";
                }
            }

        }
        return lockMap;

    }

    public StringBuilder getRegions(LoggedInConnection loggedInConnection, String regionNameStart) {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        boolean firstRegion = true;
        for (int i = 0; i < wb.getWorksheets().getNames().getCount(); i++) {
            com.aspose.cells.Name name = wb.getWorksheets().getNames().get(i);
            if (name.getText().toLowerCase().startsWith(regionNameStart) && name.getRange().getWorksheet() == azquoSheet) {
                if (firstRegion) {
                    firstRegion = false;
                } else {
                    sb.append(",");
                }
                Range dataRange = name.getRange();
                String lockMap;
                if (regionNameStart.equals(azInput) && dataRange != null) {
                    lockMap = unlockRange(dataRange);
                } else {
                    lockMap = loggedInConnection.getLockMap(name.getText().substring(dataRegionPrefix.length()).toLowerCase());
                }
                sb.append("{").append(jsonValue("name", name.getText().substring(regionNameStart.length()).toLowerCase(), false))
                        .append(jsonValue("top", dataRange != null ? dataRange.getFirstRow() : 0))
                        .append(jsonValue("left", dataRange != null ? dataRange.getFirstColumn() : 0))
                        .append(jsonValue("bottom", dataRange != null ? dataRange.getFirstRow() + dataRange.getRowCount() - 1 : 0))
                        .append(jsonValue("right", dataRange != null ? dataRange.getFirstColumn() + dataRange.getColumnCount() - 1 : 0))
                        .append(jsonRange("locks", lockMap))
                        .append("}");
            }
        }
        sb.append("]");
        return sb;
    }

    private StringBuffer cellInfo(LoggedInConnection loggedInConnection, Cell cell, boolean highlighted) {
        StringBuffer sb = new StringBuffer();
        String cellClass = createCellClass(cell.getRow(), cell.getColumn(), cell, highlighted).toString();
        String content = createCellContentHTML(loggedInConnection, cell, cellClass, null);
        if (content.startsWith("$button;name=")) {//don't show button info
            return sb;
        }
        sb.append("{");
        sb.append(jsonValue("class", cellClass, false));
        sb.append(jsonValue("value", content.replace("\"", "\\\"").replace("\n", "\\\n"), true));
        sb.append(jsonValue("id", createCellId(cell), true));
        sb.append("}");
        return sb;
    }


    public String changeValue(int row, int col, String value, LoggedInConnection loggedInConnection) {


        Cell cellChanged = azquoCells.get(row, col);
        setCellValue(cellChanged, value);


        calculateAll();
        Map<Cell, Boolean> highlighted = new HashMap<Cell, Boolean>();
        if (highlightDays > 0) {
            setHighlights(loggedInConnection, highlighted);
        }


        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (int rowNo = 0; rowNo < azquoCells.getMaxRow(); rowNo++) {
            List<String> rowValues = heldValues.get(row);
            for (int colNo = 0; colNo <= azquoCells.getMaxColumn(); colNo++) {
                Cell cell = azquoCells.get(rowNo, colNo);
                if (cell != null && cell.getType() != CellValueType.IS_NULL) {
                    String cellVal = cell.getStringValue();

                    if (rowValues.size() > colNo && !cellVal.equals(rowValues.get(colNo))) {
                        rowValues.remove(colNo);
                        rowValues.add(colNo, cellVal);
                        Boolean cellHighlighted = highlighted.get(cell);
                        if (cellHighlighted == null) cellHighlighted = false;
                        //setValue(loggedInConnection, cell, null); orig value needed when saving.  Provenance must now check that the value in sheet is the same as value on file
                        if (!azquoCells.getColumns().get(colNo).isHidden()) {
                            if (sb.length() > 1) {
                                sb.append(",");
                            }
                            sb.append(cellInfo(loggedInConnection, cell, cellHighlighted));
                        }
                        //and check that no 'selects' have changed
                        Range choiceRange = choiceRange(cell);
                        if (choiceRange != null) {
                            String choiceName = choiceMap.get(choiceRange);
                            Range chosen = getRange(choiceName + "chosen");
                            if (chosen != null) {
                                Cell chosenCell = chosen.get(cell.getRow() - choiceRange.getFirstRow(), 0);
                                //assume this is on the same row!!!!
                                int chosenCol = chosenCell.getColumn();
                                rowValues.remove(chosenCol);
                                rowValues.add(chosenCol, "");
                                if (sb.length() > 1) {
                                    sb.append(",");
                                }
                                sb.append(cellInfo(loggedInConnection, chosenCell, cellHighlighted));
                            }
                        }
                    }
                }
            }
        }
        return sb.append("]").toString();
    }

    public RegionInfo getRegionInfo(int row, int col) {
        RegionInfo regionInfo = new RegionInfo();
        for (int i = 0; i < wb.getWorksheets().getNames().getCount(); i++) {
            com.aspose.cells.Name name = wb.getWorksheets().getNames().get(i);
            if (name.getRange().getWorksheet() == azquoSheet) {
                Range r = name.getRange();
                if (name.getText().equalsIgnoreCase("az_HeadingsData")) {
                    //Admin inspect names only
                    regionInfo.row = row - r.getFirstRow() - 1;
                    regionInfo.col = col - r.getFirstColumn();
                    if (regionInfo.row < 0) return null;
                    regionInfo.region = name.getText().toLowerCase();
                    return regionInfo;
                } else {
                    if (row >= r.getFirstRow() && row < r.getFirstRow() + r.getRowCount() && col >= r.getFirstColumn() && col < r.getFirstColumn() + r.getColumnCount()) {
                        regionInfo.row = row - r.getFirstRow();
                        regionInfo.col = col - r.getFirstColumn();
                        regionInfo.region = name.getText().toLowerCase();
                        return regionInfo;
                    }
                }
            }
        }
        return null;
    }

/*    public void setValue(LoggedInConnection loggedInConnection, Cell cell, Value newValue) {
        int row = cell.getRow();
        int col = cell.getColumn();
        RegionInfo regionInfo = getRegionInfo(row, col);
        if (regionInfo == null) return;
        if (regionInfo.region.equals("az_HeadingsData")) {
            //Admin inspect names only
            return;
        } else {

            if (regionInfo.region.startsWith(dataRegionPrefix)) {
                String region = regionInfo.region.substring(dataRegionPrefix.length());
                final List<List<LoggedInConnection.ListOfValuesOrNamesAndAttributeName>> dataValueMap = loggedInConnection.getDataValueMap(region);
                final List<Integer> rowOrder = loggedInConnection.getRowOrder(region);


                if (dataValueMap != null && dataValueMap.get(regionInfo.row) != null) {
                    final List<LoggedInConnection.ListOfValuesOrNamesAndAttributeName> rowValues = dataValueMap.get(rowOrder.get(regionInfo.row));
                    List<Value> newValues = new ArrayList<Value>();
                    newValues.add(newValue);
                    rowValues.set(regionInfo.col, loggedInConnection.new ListOfValuesOrNamesAndAttributeName(newValues));
                }
            }
        }
    }*/


    public String getProvenance(LoggedInConnection loggedInConnection, int row, int col, String jsonFunction) {
        RegionInfo regionInfo = getRegionInfo(row, col);
        if (regionInfo == null) return "";
        if (regionInfo.region.equals("az_headingsdata")) {
            //Admin inspect names only
            for (Set<com.azquo.memorydb.Name> names : loggedInConnection.getValuesFound().keySet()) {
                if (regionInfo.row-- == 0) {
                    if (regionInfo.col == 0) {
                        List<Value> tempList = new ArrayList<Value>();
                        for (com.azquo.memorydb.Value value : loggedInConnection.getValuesFound().get(names)) {
                            tempList.add(value);
                        }
                        return valueService.formatCellProvenanceForOutput(loggedInConnection, tempList, jsonFunction);

                    } else {
                        for (com.azquo.memorydb.Name tempname : names) {
                            if (--regionInfo.col == 0) {
                                return valueService.formatProvenanceForOutput(tempname.getProvenance(), jsonFunction);

                            }
                        }
                    }
                }
            }
        } else {
            if (regionInfo.region.startsWith("az_displayrowheadings")) {
                String region = regionInfo.region.substring(21);

                DataRegionHeading dataRegionHeading = loggedInConnection.getRowHeadings(region).get(regionInfo.row).get(regionInfo.col);
                if (dataRegionHeading != null && dataRegionHeading.getName() != null) {
                    return valueService.formatProvenanceForOutput(dataRegionHeading.getName().getProvenance(), jsonFunction);
                } else {
                    return "";
                }
            } else if (regionInfo.region.startsWith("az_displaycolumnheadings")) {
                String region = regionInfo.region.substring(24);
                DataRegionHeading dataRegionHeading = loggedInConnection.getColumnHeadings(region).get(regionInfo.col).get(regionInfo.row);//note that the array is transposed
                if (dataRegionHeading != null && dataRegionHeading.getName() != null) {
                    return valueService.formatProvenanceForOutput(dataRegionHeading.getName().getProvenance(), jsonFunction);
                } else {
                    return "";
                }
            } else if (regionInfo.region.startsWith(dataRegionPrefix)) {
                String region = regionInfo.region.substring(dataRegionPrefix.length());
                return valueService.formatDataRegionProvenanceForOutput(loggedInConnection, region, regionInfo.row, regionInfo.col, jsonFunction);
            }
        }
        return "";
    }

    public String getAdminTableName() {
        //this routine assumes that the input data range will be called "az_input" + <SQL table name>
        for (int i = 0; i < wb.getWorksheets().getNames().getCount(); i++) {
            com.aspose.cells.Name name = wb.getWorksheets().getNames().get(i);
            if (name.getText().toLowerCase().startsWith(azInput) && name.getRange().getWorksheet() == azquoSheet) {
                return name.getText().substring(8).toLowerCase();
                //only one input range per sheet in the admin worksheet
            }
        }
        return null;
    }

    public StringBuilder getAdminData() {
        //this routine assumes that the hidden row of headings in the admin sheet will use the names from the SQL table
        for (int i = 0; i < wb.getWorksheets().getNames().getCount(); i++) {
            com.aspose.cells.Name name = wb.getWorksheets().getNames().get(i);
            if (name.getText().toLowerCase().startsWith(azInput) && name.getRange().getWorksheet() == azquoSheet) {
                //NOTE - THis routine below will add an 'id' column to the start of the range returned, even when the id is not part of the range.
                return rangeToText(name.getRange(), true);//no new names to be allocated
                //only one input range per sheet in the admin worksheet
            }
        }
        return null;
    }

    private StringBuilder rangeToText(Range range, boolean withId) {
        StringBuilder sb = new StringBuilder();
        int idCol = -1;
        int aRowNo = range.getFirstRow();
        int lastRow = aRowNo + range.getRowCount();
        if (withId) {
            for (int colNo = 0; colNo < lastRow; colNo++) {
                Cell cell = azquoCells.get(aRowNo, colNo);
                if (cell != null && cell.getStringValue().equalsIgnoreCase("id")) {
                    idCol = colNo;
                    break;
                }
            }
        }
        boolean firstRow = true;
        for (int rowNo = 0; rowNo < range.getRowCount(); rowNo++) {
            if (range.getCellOrNull(rowNo, 0) != null) {
                if (firstRow) {
                    firstRow = false;
                } else {
                    sb.append("\n");
                }
                boolean firstCol = true;
                if (idCol >= 0) {
                    // as intellij pointed out, firstrow is always false
/*                    if (firstRow) {
                        sb.append("id");
                    } else {*/
                        Cell cell = azquoCells.get(aRowNo + rowNo, idCol);
                        if (cell != null && cell.getStringValue().length() > 0) {
                            sb.append(cell.getStringValue());
                        } else {
                            sb.append("0");
                        }
                    firstCol = false;
                }
                for (int colNo = 0; colNo < range.getColumnCount(); colNo++) {
                    if (firstCol) {
                        firstCol = false;
                    } else {
                        sb.append("\t");
                    }
                    Cell cell = range.getCellOrNull(rowNo, colNo);
                    if (cell != null) {
                        String cellVal = cell.getStringValue();
                        sb.append(cellVal);
                    }
                }
            }
        }
        return sb;

    }

    private String subsequent(String nameText) {
        //adds one on to any name (e.g.   Sale 001234  _> Sale 001235)
        int i;
        for (i = nameText.length() - 1; i >= 0; i--) {
            char c = nameText.charAt(i);
            if (c < '0' || c > '9') break;
        }
        if (i < nameText.length() - 1) {
            try {
                int formatLen = nameText.length() - i - 1;
                if (formatLen > 9) formatLen = 9;

                int num = Integer.parseInt(nameText.substring(i + 1)) + 1;
                String numText = ("000000000" + num);
                if (numText.charAt(numText.length() - formatLen - 1) == '1') {
                    //overflow (e.g. 9999 ->10000)
                    numText = num + "";

                } else {
                    numText = numText.substring(numText.length() - formatLen);
                }

                nameText = nameText.substring(0, i + 1) + numText.substring(numText.length() - formatLen);
            } catch (Exception e) {
                //will not get here
            }

        }
        return nameText;
    }

    public void saveData(LoggedInConnection loggedInConnection) throws Exception {
        Map<String, String> newNames = new HashMap<String, String>();// if there are ranges starting 'az_next' then substitute these names for the latest number
        for (int i = 0; i < wb.getWorksheets().getNames().getCount(); i++) {
            com.aspose.cells.Name name = wb.getWorksheets().getNames().get(i);
            if (name.getText().toLowerCase().startsWith(azNext) && name.getRange().getWorksheet() == azquoSheet) {
                String nameText = name.getText().substring(azNext.length()).replace("_", " ");
                Name nextName = nameService.findByName(loggedInConnection, nameText);
                if (nextName != null) {
                    String nextNameName = nextName.getAttribute("next");
                    if (nextNameName != null) {
                        nextName.setAttributeWillBePersisted("next", subsequent(nextNameName));
                        newNames.put(rangeToText(name.getRange()), nextNameName);
                    }
                }
            }
        }
        for (int i = 0; i < wb.getWorksheets().getNames().getCount(); i++) {
            com.aspose.cells.Name name = wb.getWorksheets().getNames().get(i);
            if (name.getText().toLowerCase().startsWith(dataRegionPrefix) && name.getRange().getWorksheet() == azquoSheet) {
                String region = name.getText().substring(dataRegionPrefix.length());
                if (getRange("az_rowheadings" + region) == null) {
                    importRangeFromScreen(loggedInConnection, region, newNames);
                } else {
                    StringBuilder sb = rangeToText(name.getRange(), false);
                    valueService.saveData(loggedInConnection, region.toLowerCase(), sb.toString());
                }
            }
        }
    }

    public void executeSheet(LoggedInConnection loggedInConnection) throws Exception {
            loadData(loggedInConnection);
            saveData(loggedInConnection);
     }


    private String tabImage(int left, int right) {
        return "<img alt='' src = '/images/tab" + left + right + ".png'/>";
    }

    public String printTabs(StringBuilder tabs, String spreadsheetName) {
        int left;
        int right = 0;
        String tabclass;
        int lastSheet = wb.getWorksheets().getCount();
        int chosenSheet = 0;
        for (int i = 0; i < lastSheet; i++) {
            Worksheet sheet = wb.getWorksheets().get(i);
            if (sheet.getName().equals(spreadsheetName)) {
                chosenSheet = i;
                break;
            }
        }
        for (int i = 0; i < lastSheet; i++) {
            Worksheet sheet = wb.getWorksheets().get(i);
            left = right;

            if (i == chosenSheet) {
                right = 1;
                tabclass = "tabchosen";
                if (spreadsheetName == null || spreadsheetName.length() == 0) {
                    spreadsheetName = sheet.getName();
                }
            } else {
                if (i < lastSheet - 1) {
                    right = 2;
                } else {
                    right = 0;
                }
                tabclass = "tabbackground";
            }
            //tabs.append("<div class=\"tab\" style=\"left:" + left + "px\"><a href=\"#\" onclick=\"loadsheet('" + sheet.getSheetName() + "')\">" + sheet.getSheetName() + "</a></div>" + cr);
            tabs.append(tabImage(left, right)).append("<span  class=\"").append(tabclass).append("\"><a href=\"#\" onclick=\"loadsheet('")
                    .append(sheet.getName()).append("')\">").append(sheet.getName()).append("</a></span>");
        }
        tabs.append(tabImage(right, 0));
        return spreadsheetName;
    }


    public void saveBookActive(HttpServletResponse response, String fileName, String blankPath) throws Exception {
        Workbook wbOut = new Workbook(new FileInputStream(blankPath));
        wbOut.combine(wb);
        if (fileName.endsWith(".xlsx")) fileName = fileName.replace(".xlsx", ".xlsm");
        if (fileName.endsWith(".xls")) fileName += "m";
        response.setContentType("application/vnd.ms-excel"); // Set up mime type
        response.addHeader("Content-Disposition", "attachment; filename=" + fileName);
        OutputStream out = response.getOutputStream();
        wbOut.save(out, SaveFormat.XLSM);
        out.flush();
    }

    public void loadBook(String fileName, boolean useLicense) throws Exception {
        if (useLicense) {
            // should this be called on every book load or just once somewhere?
            License license = new License();
            license.setLicense(onlineService.getHomeDir() + "/aspose/Aspose.Cells.lic");
        }
        System.out.println("loaded Aspose licence");
        wb = new Workbook(new FileInputStream(fileName));
        System.out.println("file name = " + fileName);


    }


    public String convertSpreadsheetToHTML(LoggedInConnection loggedInConnection, int reportId, String spreadsheetName, StringBuilder output) throws Exception {
        setSheet(0);
        loggedInConnection.clearSortCols();
        if (spreadsheetName != null) {
            for (int i = 0; i < wb.getWorksheets().getCount(); i++) {
                Worksheet sheet = wb.getWorksheets().get(i);
                if (sheet.getName().toLowerCase().equals(spreadsheetName.toLowerCase())) {
                    setSheet(sheet.getIndex());
                }
            }
        }
        Map<Cell, Boolean> highlighted = new HashMap<Cell, Boolean>();
        String error = prepareSheet(loggedInConnection, reportId, highlighted);
        //TODO IGNORE ERROR CURRENTLY - SEND BACK IN MESSAGE
        output.append(convertToHTML(loggedInConnection, highlighted));
        return error;
    }

    public void saveBook(String fileName) throws Exception {
        wb.save(fileName);
    }

    public void saveBook(HttpServletResponse response, String fileName) throws Exception {
        if (fileName.endsWith(".xls")) fileName += "x";
        response.setContentType("application/vnd.ms-excel"); // Set up mime type
        response.addHeader("Content-Disposition", "attachment; filename=" + fileName);
        OutputStream out = response.getOutputStream();
        wb.save(out, SaveFormat.XLSX);
        out.flush();
    }


    public void saveBookAsPDF(HttpServletResponse response, String fileName) throws Exception {
        if (fileName.indexOf(".") > 0) {
            fileName = fileName.substring(0, fileName.indexOf("."));
        }
        fileName += ".pdf";
        response.setContentType("application/pdf"); // Set up mime type
        response.addHeader("Content-Disposition", "attachment; filename=" + fileName);
        OutputStream out = response.getOutputStream();
        wb.save(out, SaveFormat.PDF);
        out.flush();
    }


    public int getNumberOfSheets() {
        return wb.getWorksheets().getCount();
    }


    public String convertSheetToCSV(final String tempFileName, final int sheetNo) throws Exception {
        Worksheet sheet = wb.getWorksheets().get(sheetNo);
        String fileType = sheet.getName();
        File temp = File.createTempFile(tempFileName.substring(0, tempFileName.length() - 4), "." + fileType);
        String tempName = temp.getPath();
        temp.deleteOnExit();
        //BufferedWriter bw = new BufferedWriter(new OutputStreamWriter( new FileOutputStream(tempName), "UTF-8"));
        CsvWriter csvW = new CsvWriter(new FileWriter(tempName), '\t');
        convertRangeToCSV(sheet, csvW, null, null);
        csvW.close();
        return tempName;

    }

    private void importRangeFromScreen(LoggedInConnection loggedInConnection, String region, Map<String, String> newNames) throws Exception {
        String fileType = azquoSheet.getName();
        File temp = File.createTempFile("fromscreen", "." + fileType);
        String tempName = temp.getPath();
        temp.deleteOnExit();
        CsvWriter csvW = new CsvWriter(new FileWriter(tempName), '\t');
        convertRangeToCSV(azquoSheet, csvW, getRange("az_columnheadings" + region), null);
        convertRangeToCSV(azquoSheet, csvW, getRange("az_dataRegion" + region), newNames);
        csvW.close();
        InputStream uploadFile = new FileInputStream(tempName);
        fileType = tempName.substring(tempName.lastIndexOf(".") + 1);
        importService.readPreparedFile(loggedInConnection, uploadFile, fileType, loggedInConnection.getLanguages());
        String saveFileName = onlineService.getHomeDir() + "/databases/" + loggedInConnection.getCurrentDBName() + "/uploads/" + azquoSheet.getName() + " " + df.format(new Date()) + ".xlsx";
        File file = new File(saveFileName);
        if (file.getParentFile().mkdirs()){ // intellij said I should pay attention to the result of mkdirs, I did this
            wb.save(saveFileName, SaveFormat.XLSX);
        }
    }

    public String convertRangeToCSV(final Worksheet sheet, final CsvWriter csvW, Range range, Map<String, String> newNames) throws Exception {
        Row row;
        Cell cell;
        Cells cells = sheet.getCells();
        int rows; // No of rows
        rows = cells.getMaxRow();
        int maxCol = cells.getMaxColumn();
        int startRow = 0;
        int startCol = 0;
        if (range != null) {
            startRow = range.getFirstRow();
            startCol = range.getFirstColumn();
            rows = startRow + range.getRowCount() - 1;
            maxCol = startCol + range.getColumnCount() - 1;
        }
        for (int r = startRow; r <= rows; r++) {
            row = cells.getRows().get(r);
            if (row != null) {
                //System.out.println("Excel row " + r);
                //int colCount = 0;
                for (int c = startCol; c <= maxCol; c++) {
                    cell = cells.get(r, c);
                    //if (colCount++ > 0) bw.write('\t');
                    if (cell != null && cell.getType() != CellValueType.IS_NULL) {
                        String cellFormat = convertDates(cell.getStringValue());
                        if (newNames != null && newNames.get(cellFormat) != null) {
                            csvW.write(newNames.get(cellFormat));
                        } else {
                            csvW.write(cellFormat);
                        }
                    } else {
                        csvW.write("");
                    }
                }
                csvW.endRecord();
            }
        }
        return "";
    }

    private String convertDates(String possibleDate) {
        int slashPos = possibleDate.indexOf("/");
        if (slashPos < 0) return possibleDate;
        Date date;
        try {
            date = ukdf.parse(possibleDate);
        } catch (Exception e) {
            return possibleDate;
        }
        return df.format(date);

    }

    public String getReportName() {
        return getRangeData("az_ReportName");
    }

    public String getRangeData(String rangeName) {
        for (int i = 0; i < wb.getWorksheets().getNames().getCount(); i++) {
            com.aspose.cells.Name name = wb.getWorksheets().getNames().get(i);
            if (name.getText().equalsIgnoreCase(rangeName)) {
                return getRangeValue(name.getText());
            }
        }
        return null;
    }
}