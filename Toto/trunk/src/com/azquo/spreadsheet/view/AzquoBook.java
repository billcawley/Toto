package com.azquo.spreadsheet.view;

import com.aspose.cells.*;
import com.aspose.cells.Color;
import com.aspose.cells.Font;
import com.azquo.admin.user.UserChoiceDAO;
import com.azquo.admin.user.UserChoice;
import com.azquo.dataimport.ImportService;
import com.azquo.spreadsheet.*;
import com.csvreader.CsvWriter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JSR310Module;
import org.apache.commons.lang.math.NumberUtils;
import org.apache.log4j.Logger;
import org.springframework.ui.ModelMap;

import javax.servlet.http.HttpServletResponse;
import java.awt.*;
import java.io.*;
import java.rmi.RemoteException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;

/**
 * Created by bill on 29/04/14
 */

public class AzquoBook {

    public static final String cr = "";//removing unnecessary carriage returns
    public String errorMessage = "";


    private static final Logger logger = Logger.getLogger(AzquoBook.class);
    private SpreadsheetService spreadsheetService;
    private ImportService importService;
    private UserChoiceDAO userChoiceDAO;
    private SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");
    private SimpleDateFormat ukdf = new SimpleDateFormat("dd/MM/yy");

    public static final String azDataRegion = "az_dataregion";
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

    private int highlightDays = 0;

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

    public AzquoBook(UserChoiceDAO userChoiceDAO, SpreadsheetService spreadsheetService, ImportService importService) throws Exception {
        this.userChoiceDAO = userChoiceDAO;
        this.spreadsheetService = spreadsheetService;
        jacksonMapper.registerModule(new JSR310Module());
        this.importService = importService;
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

    // this used to take an excel style paste string, changing to use the new AzquoCells . . .
    // hacky highlighted map for Azquobook rendering
    private void fillRange(String regionName, List<List<CellForDisplay>> cellArray, boolean overwrite, Map<Cell, Boolean> highlighted) {
        // for the headings, the lockmap is "locked" rather than the full array.
        Range range = getRange(regionName);
        if (range == null || cellArray == null || cellArray.isEmpty()) return;
        insertRows(range, cellArray.size());
        insertCols(range, cellArray.get(0).size());
        range = getRange(regionName);
        createMergeMap();
        int rowNo = 0;
        for (List<CellForDisplay> row : cellArray) {
            int col = 0;
            for (CellForDisplay azquoCell : row) {
                String val = azquoCell.getStringValue();
                if (val.equals("0.0")) val = "";
                Cell currentCell = azquoCells.get(range.getFirstRow() + rowNo, range.getFirstColumn() + col);
                if (highlighted != null){
                    highlighted.put(currentCell, azquoCell.isHighlighted());
                }
                // force all azquobook to locked now
                    currentCell.getStyle().setLocked(true);
                String existingCellVal = currentCell.getStringValue();
                if (overwrite || existingCellVal == null || existingCellVal.length() == 0) {
                    setCellValue(currentCell, val);
                }
                col++;
            }
            rowNo++;
        }
    }

    // like above for headings, auto lock for example. Might be some bits to factor
    // ok I'm returning an Array of strings as some of the book code for soting needs it. It will be the bottom of columsn or the right of rows, as indicated by the boolean
    private String[] fillRange(String regionName, List<List<String>> headingArray, boolean rowHeadings) {
        // for the headings, the lockmap is "locked"
        List<String> toReturn = new ArrayList<String>();
        Range range = getRange(regionName);
        if (range == null || headingArray == null || headingArray.isEmpty()) return new String[0];
        insertRows(range, headingArray.size());
        insertCols(range, headingArray.get(0).size());
        range = getRange(regionName);
        createMergeMap();
        int rowNo = 0;
        for (List<String> row : headingArray) {
            int col = 0;
            for (String heading : row) {
                Cell currentCell = azquoCells.get(range.getFirstRow() + rowNo, range.getFirstColumn() + col);
                if (currentCell.getStringValue() == null || currentCell.getStringValue().isEmpty()){ // with headings don't overwrite existing data
                    currentCell.getStyle().setLocked(true);
                    setCellValue(currentCell, heading);
                }
                col++;
                // hacky bits to return sortable bits, not sure how this interacts with non overwritten headgins if at all
                if (rowNo == headingArray.size() - 1 && !rowHeadings) { // last row and we're doing col headings
                    toReturn.add(heading);
                }
                if (col == row.size() - 1 && rowHeadings) { // last col and we're doing row headings
                    toReturn.add(heading);
                }
            }
            rowNo++;
        }
        return toReturn.toArray(new String[toReturn.size()]);
    }

    private void fillRegion(LoggedInUser loggedInUser, String region,Map<Cell, Boolean> highlighted) throws Exception {
        logger.info("loading " + region);
        int filterCount = optionNumber(region, "hiderows");
        if (filterCount == 0) filterCount = optionNumber(region,"hiderowvalues");
        if (filterCount == 0)
            filterCount = -1;//we are going to ignore the row headings returned on the first call, but use this flag to get them on the second. Edd : what does this mean??
        int maxRows = optionNumber(region, "maxrows");
        //if (maxRows == 0) loggedInConnection.clearSortCols();//clear it.  not sure why this is done - pushes the sort from the load into the spreadsheet if there is no restriction on si
        int maxCols = optionNumber(region, "maxcols");

        Range rowHeadings = getRange("az_rowheadings" + region);
        if (rowHeadings == null) {
            throw new Exception("no range az_rowHeadings" + region);
        }
        //don't bother to display yet - maybe need to filter out or sort
        Range columnHeadings = getRange("az_columnheadings" + region);
        if (columnHeadings == null) {
            throw new Exception("no range az_ColumnHeadings" + region);
        }
        //fillRange("az_displaycolumnheadings" + region, result, "LOCKED");
        Range context = getRange("az_context" + region);
        if (context == null) {
            throw new Exception("no range az_Context" + region);
        }
        CellsAndHeadingsForDisplay cellsAndHeadingsForDisplay = spreadsheetService.getCellsAndHeadingsForDisplay(loggedInUser.getDataAccessToken(), rangeToStringLists(rowHeadings), rangeToStringLists(columnHeadings),
                rangeToStringLists(context), filterCount, maxRows, maxCols, loggedInUser.getSortRow(region), loggedInUser.getSortCol(region), highlightDays, 0);
        loggedInUser.setSentCells(region, cellsAndHeadingsForDisplay);
        // think this language detection is sound
        fillRange(dataRegionPrefix + region, cellsAndHeadingsForDisplay.getData(), true, highlighted);
        String[] givenColumnHeadings = fillRange("az_displaycolumnheadings" + region, cellsAndHeadingsForDisplay.getColumnHeadings(),false);
        String sortable = hasOption(region, "sortable");
        if (sortable != null) {
            Range displayColumnHeadings = getRange("az_displaycolumnheadings" + region);
            if (displayColumnHeadings != null) {
                givenHeadings.put("columns:" + region, givenColumnHeadings);
            }
        }
        String[] givenRowHeadings = fillRange("az_displayrowheadings" + region, cellsAndHeadingsForDisplay.getRowHeadings(),true);
        if (sortable != null && sortable.equalsIgnoreCase("all")) {
            Range displayRowHeadings = getRange("az_displayrowheadings" + region);
            if (displayRowHeadings != null) {
                givenHeadings.put("rows:" + region, givenRowHeadings);
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
            if (operator == '>') {//interpret the '>' symbol as '-' to create an integer
                options = "-" + options.substring(1);
            } else {
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
        if (item.contains("'")) {
            content = "<option value = \"" + item + "\"";
        } else {
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

    public void fillVelocityOptionInfo(LoggedInUser loggedInUser, ModelMap model) {
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
        model.addAttribute("hdays", highlightDays + "");
        model.addAttribute("menuregions", vRegions);
        model.addAttribute("regions", getRegions(loggedInUser, dataRegionPrefix).toString());//this is for javascript routines
    }

    public void loadData(LoggedInUser loggedInUser, Map<Cell, Boolean> highlighted) throws Exception {
        errorMessage = "";
        calculateAll();
        for (int i = 0; i < wb.getWorksheets().getNames().getCount(); i++) {
            com.aspose.cells.Name name = wb.getWorksheets().getNames().get(i);
            if (name.getText().toLowerCase().startsWith(dataRegionPrefix) && name.getRange().getWorksheet() == azquoSheet) {
                String regionName = name.getText().substring(dataRegionPrefix.length()).toLowerCase();
                long regStart = System.currentTimeMillis();
                try {
                    fillRegion(loggedInUser, regionName,highlighted);
                } catch (RemoteException re) {
                    Throwable t = re.detail.getCause();
                    if (t != null){
                        errorMessage = t.getLocalizedMessage();
                        t.printStackTrace();
                    } else {
                        errorMessage = re.getMessage();
                        re.printStackTrace();
                    }
                }catch (Exception e) {
                    errorMessage = e.getMessage();
                    e.printStackTrace();
                }
                System.out.println("fillregion took " + (System.currentTimeMillis() - regStart) + " millisecs");
                regStart = System.currentTimeMillis();
                calculateAll();
                System.out.println("calcall took " + (System.currentTimeMillis() - regStart) + " millisecs");
            }
        }
    }

    public String printAllStyles() {
        StringBuilder sb = new StringBuilder();
        for (String className : shortStyles.keySet()) {
            sb.append(".").append(shortStyles.get(className)).append(" {").append(className).append("}\n");
        }
        return sb.toString();
    }

    private void setChoices(LoggedInUser loggedInUser, int reportId) {
        createChosenMap();
        createChoiceMap();//to check when choice cells may be changed
        for (Range range : chosenMap.keySet()) {
            if (range.getRowCount() == 1 && range.getColumnCount() == 1) {
                String choiceName = chosenMap.get(range);
                Range choice = getRange(choiceName + "choice");
                if (choice != null) {
                    UserChoice userChoice = userChoiceDAO.findForUserIdReportIdAndChoice(loggedInUser.getUser().getId(), reportId, choiceName);
                    if (userChoice != null) {
                        range.setValue(userChoice.getChoiceValue());
                    }
                }
            }
        }
        calculateAll();
        List<UserChoice> allChoices = userChoiceDAO.findForUserIdAndReportId(loggedInUser.getUser().getId(), reportId);
        for (UserChoice uc : allChoices) {
            String choiceName = uc.getChoiceName();
            if (choiceName.startsWith(OPTIONPREFIX)) {
                sheetOptions.put(choiceName, uc.getChoiceValue());
            }
        }
    }

    private void setSortCols(LoggedInUser loggedInUser, int reportId) {
        for (int i = 0; i < wb.getWorksheets().getNames().getCount(); i++) {
            com.aspose.cells.Name name = wb.getWorksheets().getNames().get(i);
            if (name.getText().toLowerCase().startsWith(dataRegionPrefix) && name.getRange().getWorksheet() == azquoSheet) {
                String region = name.getText().substring(dataRegionPrefix.length());
                UserChoice userSortCol = userChoiceDAO.findForUserIdReportIdAndChoice(loggedInUser.getUser().getId(), reportId, "sort " + region + " by row");
                if (userSortCol != null) {
                    try {
                        String choiceVal = userSortCol.getChoiceValue();
                        sortChoices.put("rows:" + region, choiceVal);
                        loggedInUser.setSortRow(region, choiceVal);
                    } catch (Exception ignored) {
                        //set by the system, so should not have an exception
                    }
                }
                UserChoice userSortRow = userChoiceDAO.findForUserIdReportIdAndChoice(loggedInUser.getUser().getId(), reportId, "sort " + region + " by column");
                if (userSortRow != null) {
                    try {
                        sortChoices.put("columns:" + region, userSortRow.getChoiceValue());
                        loggedInUser.setSortCol(region, userSortRow.getChoiceValue());
                    } catch (Exception ignored) {
                        //set by the system, so should not have an exception
                    }
                }
            }
        }
    }

    public void prepareSheet(LoggedInUser loggedInUser, int reportId, Map<Cell, Boolean> highlighted) throws Exception {
        calcMaxCol();
        setChoices(loggedInUser, reportId);
        setSortCols(loggedInUser, reportId);
        UserChoice highlight = userChoiceDAO.findForUserIdReportIdAndChoice(loggedInUser.getUser().getId(), reportId, "highlight");
        if (highlight != null) {
            try {
                highlightDays = Integer.parseInt(highlight.getChoiceValue());
            } catch (Exception ignored) {
                //missing the highlight would not be a problem
            }
        }
        loadData(loggedInUser, highlighted);
        //find the cells that will be used to make choices...
        calcMaxCol();
        setupColwidths();
        setupMaxHeight();
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

    private Range chosenRange(Cell cell) {
        return cellInMap(cell, chosenMap);
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

    private String createCellContentHTML(LoggedInUser loggedInUser, Cell cell, String cellClass, String overrideValue) {
        String content = getCellContentAsString(cell);
        // changed to rotation angle from rotation as depreciated, I assume the same value
        if (overrideValue != null) {
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
                List<String> choiceList = new ArrayList<String>();
                String cellChoice = choice.get(choiceRow, 0).getStringValue();
                List<String> constants = new ArrayList<String>();
                if (cellChoice.startsWith("\"") || cellChoice.startsWith("“")) {
                    constants = interpretList(cellChoice);
                } else {
                    try {
                        System.out.println("SELECTION: choosing from " + cellChoice);
                        choiceList = spreadsheetService.getDropDownListForQuery(loggedInUser.getDataAccessToken(), cellChoice, loggedInUser.getLanguages());
                    } catch (Exception e) {
                        constants.add(e.getMessage());
                    }
                }
                String origContent = content;
                if (constants.size() > 0 || (choiceList.size() > 0 && choiceList.size() < 1500)) {
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
                        if (constant!=null){
                            content += addOption(constant, origContent);
                        }
                    }
                    // now using a prepared shoicelist from above, hope it won't break anything
                    for (String nameString : choiceList) {
                        content += addOption(nameString, origContent);
                    }
                    content += "</select>";
                } else {
                    if (choiceList.size() > 1499){
                        content = "<input class = '" + selectClass + "' type='text' oninput='`chooseFromList(\"" + choiceName + "\")' id='select" + choiceName + "' class='" + cellClass + "' value='" + origContent + "' />" + cr;
                    }else {
                    content = "";
                    }
                    cell.setValue("");

                }
            }
        }
        return content;
    }

    private void createSortableHeadings() {
        for (String range : givenHeadings.keySet()) {
            String type = range.substring(0, range.indexOf(":") - 1);
            String region = range.substring(type.length() + 2);
            Range displayHeadings = getRange("az_display" + type + "headings" + region);
            sortableHeadings.put(displayHeadings, range);
        }
    }

    public StringBuffer convertToHTML(LoggedInUser loggedInUser, Map<Cell, Boolean> highlighted) {
        StringBuffer output = new StringBuffer();
        createSortableHeadings();
        createChosenMap();//the chosen cells may have moved
        int rowNo = 0;
        int shownRowNo = 0;
        int cellTop = 0;
        Row lastRow = null;
        while (rowNo <= azquoCells.getMaxRow()) {
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
                        String origContent;
/*                        if (cell.getStyle().getRotationAngle() == 90) {
                            content = "<div class='r90'>" + content + "</div>";
                        }*/
                        Boolean cellHighlighted = null;
                        if (highlighted != null){
                            cellHighlighted = highlighted.get(cell);
                        }
                        if (cellHighlighted == null) cellHighlighted = false;

                        StringBuilder cellClass = createCellClass(rowNo, colNo, cell, cellHighlighted);
                        String overrideValue = null;
                        if (shownRowNo > 500) {
                            overrideValue = "<br/><b>Spreadsheet truncated.  To see full report, download as XLSX</b>";
                        }
                        String content = createCellContentHTML(loggedInUser, cell, cellClass.toString(), overrideValue);
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
                            String[] givenStrings = givenHeadings.get(headingsRegion);
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
                        output.append("   <div class='").append(cellClass).append("' ").append(sizeInfo).append(" id='cell")
                                .append(rowNo).append("-").append(colNo).append("'>").append(content.trim()).append("</div>" + cr);
                        if (shownRowNo > 500) {
                            return output;
                        }
                    }
                }
                cellTop += rowHeight + 1;
            }
            rowNo++;
            lastRow = row;
        }
        return output;
    }

    public StringBuilder drawCharts(LoggedInUser loggedInUser, String basePath) throws Exception {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < azquoSheet.getCharts().getCount(); i++) {
            Chart chart = azquoSheet.getCharts().get(i);
            String name = chart.getName();
            //File tempchart = File.createTempFile(loggedInConnection.getConnectionId() + name ,".jpg");
            String tempname = (loggedInUser.getUser().getId() + "." + loggedInUser.getReportId() + "." + name + ".png").replace(" ", "");
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

    private StringBuilder jsonValue(String name, int val) {
        return jsonValue(name, val + "", true);
    }

    private StringBuilder jsonValue(String name, String value, boolean withComma) {
        StringBuilder sb = new StringBuilder();
        if (withComma) sb.append(",");
        sb.append("\"").append(name).append("\":\"").append(value).append("\"");
        return sb;
    }

    // manual creating of json but this should be zappe fairly shortly

    public StringBuilder getRegions(LoggedInUser loggedInUser, String regionNameStart) {
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


                loggedInUser.getSentCells(name.getText().substring(dataRegionPrefix.length()));


                // edd zapped the locks, I wonder if this will cause a problem?
                Range dataRange = name.getRange();
                sb.append("{").append(jsonValue("name", name.getText().substring(regionNameStart.length()).toLowerCase(), false))
                        .append(jsonValue("top", dataRange != null ? dataRange.getFirstRow() : 0))
                        .append(jsonValue("left", dataRange != null ? dataRange.getFirstColumn() : 0))
                        .append(jsonValue("bottom", dataRange != null ? dataRange.getFirstRow() + dataRange.getRowCount() - 1 : 0))
                        .append(jsonValue("right", dataRange != null ? dataRange.getFirstColumn() + dataRange.getColumnCount() - 1 : 0))
                        .append(loggedInUser.getSentCells(name.getText().substring(dataRegionPrefix.length())) != null ? jsonLockRange("locks", loggedInUser.getSentCells(name.getText().substring(dataRegionPrefix.length())).getData()) : "")
                        .append("}");
            }
        }
        sb.append("]");
        return sb;
    }
    // skip the middle man I don't want to make a string array then convert to JSON, this should do the trick

    private StringBuilder jsonLockRange(String name, List<List<CellForDisplay>> azquoCells) {
        StringBuilder sb = new StringBuilder();
        sb.append(",\"").append(name).append("\":[");
        if (azquoCells != null) {
            boolean firstRow = true;
            for (List<CellForDisplay> cells : azquoCells) {
                boolean firstCol = true;
                if (firstRow) {
                    firstRow = false;
                } else {
                    sb.append(",");

                }
                sb.append("[");
                for (CellForDisplay cell : cells) {
                    if (firstCol) {
                        firstCol = false;
                    } else {
                        sb.append(",");
                    }
                    if (cell.isLocked()) {
                        sb.append("\"LOCKED\"");
                    } else {
                        sb.append("\"\"");
                    }
                }
                sb.append("]");

            }
        }
        sb.append("]");
        return sb;
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

    public String getProvenance(LoggedInUser loggedInUser, int row, int col, String jsonFunction) throws Exception {
        RegionInfo regionInfo = getRegionInfo(row, col);
        //todo make provenance on headings work again
        if (regionInfo == null) return "";
            if (regionInfo.region.startsWith("az_displayrowheadings")) {
                String region = regionInfo.region.substring(21);
                List<List<String>> rowHeadings = loggedInUser.getSentCells(region).getRowHeadings();
                String heading = rowHeadings.get(regionInfo.row).get(regionInfo.col);
                if (heading != null) {
                    return spreadsheetService.formatRowHeadingProvenanceForOutput(loggedInUser, region, regionInfo.row, regionInfo.col, jsonFunction);
                } else {
                    return "";
                }
            } else if (regionInfo.region.startsWith("az_displaycolumnheadings")) {
                String region = regionInfo.region.substring(24);
                List<List<String>> colHeadings = loggedInUser.getSentCells(region).getColumnHeadings();
                // this was transposed (col/row reveresed) I don't think that's right any more
                String heading = colHeadings.get(regionInfo.row).get(regionInfo.col);
                if (heading != null) {
                    return spreadsheetService.formatColumnHeadingProvenanceForOutput(loggedInUser, region, regionInfo.row, regionInfo.col, jsonFunction);
                } else {
                    return "";
                }
            } else if (regionInfo.region.startsWith(dataRegionPrefix)) {
                String region = regionInfo.region.substring(dataRegionPrefix.length());
                return spreadsheetService.formatDataRegionProvenanceForOutput(loggedInUser, region, regionInfo.row, regionInfo.col, jsonFunction);
            }
        return "";
    }

    // Changing to return 2d string array, this is what we want to pass to the back end

    private List<List<String>> rangeToStringLists(Range range) {
        List<List<String>> toReturn = new ArrayList<List<String>>();
        for (int rowNo = 0; rowNo < range.getRowCount(); rowNo++) {
            List<String> row = new ArrayList<String>();
            toReturn.add(row);
            if (range.getCellOrNull(rowNo, 0) != null) {
                for (int colNo = 0; colNo < range.getColumnCount(); colNo++) {
                    Cell cell = range.getCellOrNull(rowNo, colNo);
                    row.add(cell != null ? cell.getStringValue().trim() : null);
                }
            }
        }
        return toReturn;
    }

    // leaving in for the mo, may be used for batch processing later . . .

    public void saveData(LoggedInUser loggedInUser) throws Exception {
        Map<String, String> newNames = new HashMap<String, String>();// if there are ranges starting 'az_next' then substitute these names for the latest number
        for (int i = 0; i < wb.getWorksheets().getNames().getCount(); i++) {
            com.aspose.cells.Name name = wb.getWorksheets().getNames().get(i);
            if (name.getText().toLowerCase().startsWith(dataRegionPrefix) && name.getRange().getWorksheet() == azquoSheet) {
                String region = name.getText().substring(dataRegionPrefix.length());
                if (getRange("az_rowheadings" + region) == null) {
                    importRangeFromScreen(loggedInUser, region, newNames);
                } else {
                    spreadsheetService.saveData(loggedInUser, region.toLowerCase());
                }
            }
        }
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
            license.setLicense(spreadsheetService.getHomeDir() + "/aspose/Aspose.Cells.lic");
        }
        System.out.println("loaded Aspose licence");
        wb = new Workbook(new FileInputStream(fileName));
        System.out.println("file name = " + fileName);
    }


    public String convertSpreadsheetToHTML(LoggedInUser loggedInUser, int reportId, String spreadsheetName, StringBuilder output) throws Exception {
        setSheet(0);
        loggedInUser.clearSortCols();
        if (spreadsheetName != null) {
            for (int i = 0; i < wb.getWorksheets().getCount(); i++) {
                Worksheet sheet = wb.getWorksheets().get(i);
                if (sheet.getName().toLowerCase().equals(spreadsheetName.toLowerCase())) {
                    setSheet(sheet.getIndex());
                }
            }
        }
        Map<Cell, Boolean> highlighted = new HashMap<Cell, Boolean>();
        prepareSheet(loggedInUser, reportId, highlighted);
        //TODO IGNORE ERROR CURRENTLY - SEND BACK IN MESSAGE
        output.append(convertToHTML(loggedInUser, highlighted));
        return errorMessage;
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
        boolean transpose = false;
        String fileType = sheet.getName();
        if (fileType.toLowerCase().contains("transpose")){
            transpose= true;
        }
        File temp = File.createTempFile(tempFileName.substring(0, tempFileName.length() - 4), "." + fileType);
        String tempName = temp.getPath();
        temp.deleteOnExit();
        //BufferedWriter bw = new BufferedWriter(new OutputStreamWriter( new FileOutputStream(tempName), "UTF-8"));
        CsvWriter csvW = new CsvWriter(new FileWriter(tempName), '\t');
        convertRangeToCSV(sheet, csvW, null, null, transpose);
        csvW.close();
        return tempName;
    }

    // edd has re-enabled for batch processing but not entirely sold on this . . .

    private void importRangeFromScreen(LoggedInUser loggedInUser, String region, Map<String, String> newNames) throws Exception {
        String fileType = azquoSheet.getName();
        File temp = File.createTempFile("fromscreen", "." + fileType);
        String tempName = temp.getPath();
        temp.deleteOnExit();
        CsvWriter csvW = new CsvWriter(new FileWriter(tempName), '\t');
        convertRangeToCSV(azquoSheet, csvW, getRange("az_columnheadings" + region), null, false);
        convertRangeToCSV(azquoSheet, csvW, getRange("az_dataRegion" + region), newNames, false);
        csvW.close();
        fileType = tempName.substring(tempName.lastIndexOf(".") + 1);
        importService.readPreparedFile(loggedInUser.getDataAccessToken(), tempName, fileType, loggedInUser.getLanguages());
        String saveFileName = spreadsheetService.getHomeDir() + "/databases/" + loggedInUser.getDatabase().getName() + "/uploads/" + azquoSheet.getName() + " " + df.format(new Date()) + ".xlsx";
        File file = new File(saveFileName);
        if (file.getParentFile().mkdirs()) { // intellij said I should pay attention to the result of mkdirs, I did this
            wb.save(saveFileName, SaveFormat.XLSX);
        }
    }

    private void writeCell(Cells cells, int r, int c, CsvWriter csvW, Map<String,String> newNames) throws Exception{
        Cell cell = cells.get(r, c);
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



    public void convertRangeToCSV(final Worksheet sheet, final CsvWriter csvW, Range range, Map<String, String> newNames, boolean transpose) throws Exception {
        Row row;
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

        if (!transpose) {
            for (int r = startRow; r <= rows; r++) {
                row = cells.getRows().get(r);
                if (row != null) {
                    //System.out.println("Excel row " + r);
                    //int colCount = 0;
                    for (int c = startCol; c <= maxCol; c++) {
                        writeCell(cells,r,c,csvW,newNames);
                    }
                    csvW.endRecord();
                }
            }
        }else {
            for (int c = startCol; c <= maxCol; c++) {
                for (int r = startRow; r <= rows; r++) {
                    writeCell(cells, r, c, csvW, newNames);
                }
                csvW.endRecord();

            }
        }
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