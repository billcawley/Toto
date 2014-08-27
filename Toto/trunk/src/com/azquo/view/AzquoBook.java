package com.azquo.view;

import com.aspose.cells.*;
import com.azquo.admindao.UserChoiceDAO;
import com.azquo.adminentities.UserChoice;
import com.azquo.memorydb.*;
import com.azquo.memorydb.Name;
import com.azquo.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang.math.NumberUtils;
import org.apache.log4j.Logger;
import org.apache.poi.ss.usermodel.CellValue;


import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;




/**
 * Created by bill on 29/04/14.
 */
public  class AzquoBook {


    private static final Logger logger = Logger.getLogger(AzquoBook.class);
    private ValueService valueService;
    private AdminService adminService;
    private NameService nameService;
    private UserChoiceDAO userChoiceDAO;

    public static final String azDataRegion = "az_dataregion";
    public static final String azInput = "az_input";

    private static final ObjectMapper jacksonMapper = new ObjectMapper();

    class SortableInfo{
        String region;
        int lastCol;
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

    class RegionInfo{
        String region;
        int row;
        int col;
    }

    String sortRegion = "";
    int sortColumn = 0;
    private Map<Integer, Integer> sortRows = null;//this is for 'display only' sort on the current sheet.
    private Map<Integer, Integer> unSortRows = null;// reversing above
    private Workbook wb = null;
    private Worksheet azquoSheet = null;
    private Cells azquoCells;
    private List<List<String>> heldValues = new ArrayList<List<String>>();
    private Appendable output = null;
    int maxWidth;
    public String dataRegionPrefix = null;
    public String nameChosenJson = null;  // used only for passing the parameter when admin/inspection chooses a name
    int topCell = -1;
    int leftCell = -1;
    List<Integer> colWidth = new ArrayList<Integer>();
    // now add css for each used style
    Map<String,String> shortStyles = new HashMap<String, String>();
    Map <Style,Style> highlightStyles = new HashMap<Style, Style>();


    Map<Cell, String> choiceMap = null;
    Map<Cell, CellArea> mergedCells = null;
    //HSSFPalette Colors = null;

    private static final Map<Short, String> ALIGN = mapFor(TextAlignmentType.LEFT, "left",
            TextAlignmentType.CENTER, "center", TextAlignmentType.RIGHT, "right", TextAlignmentType.FILL, "left",
            TextAlignmentType.JUSTIFY, "left", TextAlignmentType.CENTER, "center");

    private static final Map<Short, String> VERTICAL_ALIGN = mapFor(
            TextAlignmentType.BOTTOM, "bottom", TextAlignmentType.CENTER, "middle", TextAlignmentType.TOP,
            "top");
    // below adjusted dashed to solid
    private static final Map<Short, String> BORDER = mapFor(CellBorderType.DASH_DOT,
            "solid 1pt", CellBorderType.DASH_DOT_DOT, "solid 1pt", CellBorderType.DASHED,
            "solid 1pt", CellBorderType.DOTTED, "solid 1pt", CellBorderType.DOUBLE,
            "double 3pt", CellBorderType.HAIR, "solid 1px", CellBorderType.MEDIUM, "solid 2pt",
            CellBorderType.MEDIUM_DASH_DOT, "dashed 2pt", CellBorderType.MEDIUM_DASH_DOT_DOT,
            "solid 2pt", CellBorderType.MEDIUM_DASHED, "solid 2pt", CellBorderType.NONE,
            "1px solid silver", CellBorderType.SLANTED_DASH_DOT, "solid 2pt", CellBorderType.THICK,
            "solid 3pt", CellBorderType.THIN, "solid 1pt");

    private static final Map<Short, String> BORDERSIZE = mapFor(CellBorderType.DASH_DOT,
            "1", CellBorderType.DASH_DOT_DOT, "1", CellBorderType.DASHED,
            "1", CellBorderType.DOTTED, "1", CellBorderType.DOUBLE,
            "1", CellBorderType.HAIR, "1", CellBorderType.MEDIUM, "2",
            CellBorderType.MEDIUM_DASH_DOT, "2", CellBorderType.MEDIUM_DASH_DOT_DOT,
            "2", CellBorderType.MEDIUM_DASHED, "2", CellBorderType.NONE,
            "1", CellBorderType.SLANTED_DASH_DOT, "2", CellBorderType.THICK,
            "3", CellBorderType.THIN, "1");




    int maxCol = 0;
    int maxHeight = 0;


    private int borderBottomWidth = 0;
    private int borderRightWidth = 0;
    private Formatter sOut;
    Map <Cell, Set<Cell>> dependencies = null;//this map shows what calculations are dependent on each other.  if 'Cell' is changed then Set<Cell> must all be calculated
    Map <Cell, SortableInfo> sortableHeadings = new HashMap<Cell, SortableInfo>();

    private static final int ROWSCALE = 16;
    private static final int COLUMNSCALE = 40;

    private int highlightDays = 0;

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

    public AzquoBook(final ValueService valueService, AdminService adminService, NameService nameService, UserChoiceDAO userChoiceDAO) throws Exception {
        this.valueService = valueService;
        this.adminService = adminService;
        this.userChoiceDAO = userChoiceDAO;
        this.nameService = nameService;

    }




    public int getMaxHeight(){   return maxHeight;  }
    public int getMaxWidth(){    return maxWidth;   }
    public int getTopCell(){     return topCell;    }
    public int getLeftCell(){    return leftCell;   }
    public int getMaxRow(){      return azquoCells.getMaxRow(); }
    public int getMaxCol() {     return maxCol;     }



    public void setSheet(int sheetNo){
        azquoSheet =  wb.getWorksheets().get(sheetNo);
        azquoCells = azquoSheet.getCells();
        createMergeMap();

    }


    private void createMergeMap() {
        mergedCells = new HashMap<Cell, CellArea>();
        for (CellArea r:(List<CellArea>)azquoCells.getMergedCells()) {
            mergedCells.put(azquoCells.get(r.StartRow, r.StartColumn), r);
            //mergeMap.put(r.)
        }

    }

    private Range getRange(String rangeName){
        for (int i = 0;i < wb.getWorksheets().getNames().getCount();i++){
            com.aspose.cells.Name name = wb.getWorksheets().getNames().get(i);
            if (name.getText().equalsIgnoreCase(rangeName)){
                return name.getRange();

            }
        }
        return null;
    }





    public String getRangeValue(String rangeName){
        Range range = wb.getWorksheets().getRangeByName(rangeName);
        if (range!=null){
            return range.getCellOrNull(0,0).getStringValue();
        }
        return null;
    }

    private Range interpretRangeName(String cellString) {

        return wb.getWorksheets().getRangeByName(cellString);
    }


    private void createChoiceMap() {
        choiceMap = new HashMap<Cell, String>();
        for (int i = 0;i < wb.getWorksheets().getNames().getCount();i++){
            com.aspose.cells.Name name = wb.getWorksheets().getNames().get(i);
            if (name.getText().toLowerCase().endsWith("chosen") && name.getRange().getWorksheet() == azquoSheet) {
                Range range = name.getRange();
                if (range!=null) {
                    choiceMap.put(range.getCellOrNull(0, 0), name.getText().substring(0, name.getText().length() - 6).toLowerCase());
                }
            }
        }
    }



    public void calculateAll() {

        wb.calculateFormula();

    }
    private String rangeToText(Range range) {
        return rangeToText(range,false).toString();
    }


    private void setupColwidths() {
        maxWidth = 0;
        colWidth = new ArrayList<Integer>();
        for (int c = 0; c <= maxCol; c++) {
            int colW =   azquoCells.getColumnWidthPixel(c);
            shortStyles.put("left:" + maxWidth + "px;width:" + colW + "px","cp" + c);
            colWidth.add(colW);
            maxWidth += colW + 1;
        }

    }

    private void setupMaxHeight(){
        maxHeight = 0;
        Row lastRow = null;
        for (int rowNo = 0; rowNo < azquoCells.getMaxRow();rowNo++){
            maxHeight += azquoCells.getRowHeightPixel(rowNo);
        }
    }

    private void insertRows(Range range, int rowCount) {

        int existingRows = range.getRowCount();
        if (existingRows < rowCount) {
            int copyRow = range.getFirstRow() + existingRows - 2;
            azquoCells.insertRows(copyRow + 1, rowCount - existingRows);
            for (int rowNo = copyRow + 1; rowNo < range.getFirstRow() + rowCount - 1;rowNo++){

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
            for (int colNo = copyCol + 1; colNo < range.getFirstColumn() + colCount - 1;colNo++){

                azquoCells.copyColumn(azquoCells, copyCol, colNo);
            }

            calculateAll();

        }
    }



    private void setCellValue(Cell currentCell, String val){
        if (currentCell.getFormula()!=null){
            return;
        }
        if (val.length()==0){
            currentCell.setValue(null);
            return;
        }
        if (val.endsWith("%") && NumberUtils.isNumber(val.substring(0, val.length() - 1))){
            currentCell.setValue(Double.parseDouble(val.substring(0,val.length()-1))/100);
        }else{
            if (val.startsWith("$") || val.startsWith("£")){//remove £ or $ prefix - what about other currencies???
                val = val.substring(1);
            }
            if (NumberUtils.isNumber(val)) {
                currentCell.setValue(Double.parseDouble(val));
            } else {
                currentCell.setValue(val);
             }
        }
    }

    private void setHighlights(LoggedInConnection loggedInConnection){
        //NOTE - This may change the formatting of model cells for conditional formats, which may make a mess!

        for (int i = 0;i < wb.getWorksheets().getNames().getCount();i++){
            com.aspose.cells.Name name = wb.getWorksheets().getNames().get(i);
            if (name.getText().toLowerCase().startsWith(dataRegionPrefix) && name.getRange().getWorksheet() == azquoSheet) {
                Range range = name.getRange();
                String region = name.getText().substring(dataRegionPrefix.length());
                if (range == null) {
                    return;
                }
                for (int rowNo = 0; rowNo < range.getRowCount(); rowNo++) {
                    for (int colNo = 0; colNo < range.getColumnCount(); colNo++) {
                        Cell cell = range.getCellOrNull(rowNo, colNo);
                        if (cell != null) {

                            if (highlightDays >= valueService.getAge(loggedInConnection, region, rowNo, colNo)) {
                                cell.setStyle(highlightStyle(cell));
                                // int j = wb.getFontAt(cell.getStyle().getFontIndex()).getColor();

                            }
                        }
                    }
                }
            }
        }
    }


    private void fillRange(String regionName, String fillText, String lockMap) {
        // for the headins, the lockmap is "locked" rather than the full array.
        boolean shapeAdjusted = false;
        Range range = wb.getWorksheets().getRangeByName(regionName);
        if (range==null) return;
        String[] rows  = fillText.split("\n", -1);

        String[] rowLocks = lockMap.split("\n", -1);
        for (int rowNo = 0; rowNo <rows.length;rowNo++) {
            String row = rows[rowNo];
            String rowLock = rowLocks[0];
            if (rowNo < rowLocks.length){
                rowLock = rowLocks[rowNo];
            }
            String[] vals = row.split("\t", -1);
            String[] locks = rowLock.split("\t",-1);
            int colCount = vals.length;
            if (!shapeAdjusted) {
                int rowCount = rows.length;
                insertRows(range, rowCount);
                insertCols(range, colCount);
                range = wb.getWorksheets().getRangeByName(regionName);
                createMergeMap();
                shapeAdjusted = true;
            }
            for (int col=0;col < vals.length;col++){
                String val = vals[col];
                String locked = locks[0];
                if (col < locks.length){
                    locked = locks[col];
                }
                if (val.equals("0.0")) val = "";
                Cell currentCell = azquoCells.get(range.getFirstRow() + rowNo, range.getFirstColumn() + col);
                if (locked.equals("LOCKED")){
                    currentCell.getStyle().setLocked(true);
                }
                setCellValue(currentCell,val);

            }
        }

    }

    private String colToLetters(int column){
        if (column < 27){
            char c = (char)(column + 64);

            return c + "";
        }
        char firstChar = 64;
        while (column > 26){
            firstChar++;
            column -=26;
        }
        return firstChar + "" + (char)(column + 64);
    }

    private String rangeToString(Range range){
        return (colToLetters(range.getFirstColumn() + 1) + (range.getFirstRow() + 1) + ":" + colToLetters(range.getFirstColumn() + range.getColumnCount()) + (range.getFirstRow() + range.getRowCount()));
    }

    private String fillRegion(LoggedInConnection loggedInConnection, String region) throws Exception {



        logger.info("loading " + region);

        int filterCount = optionNumber(region, "hiderows");
        if (filterCount == 0)
            filterCount = -1;//we are going to ignore the row headings returned on the first call, but use this flag to get them on the second.
        int maxRows = optionNumber(region, "maxrows");

        Range rowHeadings = getRange("az_rowheadings" + region);
        if (rowHeadings == null) {
            return "error: no range az_RowHeadings" + region;
        }
        String headings = rangeToText(rowHeadings);
        if (headings.startsWith("error:")) {
            return headings;
        }
        String result = valueService.getFullRowHeadings(loggedInConnection, region, headings);
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
        result = valueService.getColumnHeadings(loggedInConnection, region, headings);
        if (result.startsWith("error:")) return result;
        if (hasOption(region,"sortable")!=null){
            Range displayColumnHeadings = getRange("az_displaycolumnheadings" + region);
            if (displayColumnHeadings!=null){
                Cell lastLineStart = displayColumnHeadings.getCellOrNull(displayColumnHeadings.getRowCount() - 1, 0);
                if (lastLineStart !=null){
                    SortableInfo si = new SortableInfo();
                    si.region = region;
                    si.lastCol = displayColumnHeadings.getFirstColumn() + displayColumnHeadings.getColumnCount() -1;
                    sortableHeadings.put(lastLineStart,si);
                }

            }
        }

        fillRange("az_displaycolumnheadings" + region, result, "LOCKED");
        Range context = getRange("az_context" + region);
        if (context == null) {
            return "error: no range az_Context" + region;
        }
        headings = rangeToText(context);
        if (headings.startsWith("error:")) {
            return headings;
        }
        result = valueService.getDataRegion(loggedInConnection, headings, region, filterCount, maxRows);
        if (result.startsWith("error:")) return result;
        fillRange(dataRegionPrefix + region, result,loggedInConnection.getLockMap(region));
        result = valueService.getRowHeadings(loggedInConnection, region, headings, filterCount);
        fillRange("az_displayrowheadings" + region, result, "LOCKED");

        //then percentage, sort, trimrowheadings, trimcolumnheadings, setchartdata
        String chartName =hasOption(region,"chart");
        if (chartName!=null){
           for (int chartNo = 0;chartNo < azquoSheet.getCharts().getCount();chartNo++){
                    Chart chart = azquoSheet.getCharts().get(chartNo);

                if (chart.getName().equalsIgnoreCase(chartName)){
                    boolean isVertical = chart.getNSeries().get(0).isVerticalValues();
                    Range displayColumnHeadings = getRange("az_displaycolumnheadings" + region);
                    Range displayRowHeadings = getRange("az_displayrowheadings" + region);
                    chart.getNSeries().clear();
                    chart.getNSeries().addR1C1("R[" + (displayColumnHeadings.getFirstRow()) + "]C["
                                                    + (displayRowHeadings.getFirstColumn()) + "]:R["
                                                    + (displayRowHeadings.getFirstRow() + displayRowHeadings.getRowCount()-1) + "]C["
                                                    + (displayColumnHeadings.getFirstColumn() + displayColumnHeadings.getColumnCount()-1) + "]",isVertical);


                }

            }
        }

        return "";

    }


    private Style highlightStyle(Cell cell){
        Style highlight = highlightStyles.get(cell.getStyle());
        if (highlight == null){
            highlight = wb.createStyle();
            highlight.copy(cell.getStyle());
            highlight.getFont().setColor(Color.getRed());
            highlightStyles.put(cell.getStyle(), highlight);
        }
        return highlight;

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
        Range optionrange = getRange("az_options" + region);
        if (optionrange == null)
            return null;
        String options = optionrange.getCellOrNull(0, 0).getStringValue();
        int foundPos = options.toLowerCase().indexOf(optionName.toLowerCase());
        if (foundPos < 0) {
            return null;
        }
        if (options.length()> foundPos + optionName.length()) {
            options = options.substring(foundPos + optionName.length() + 1).trim();//allow for a space or '=' at the end of the option name
            foundPos = options.indexOf(",");
            if (foundPos > 0) {
                options = options.substring(0, foundPos);
            }
            return options;
        }
        return "";
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
        if (item.toLowerCase().equals(selected.toLowerCase())) {
            content += " selected";

        }
        content += ">" + item + "</option>\n";
        return content;
    }

    private void calcMaxCol(){

        maxCol = azquoCells.getMaxColumn();

    }




    private void addStyle( String styleName, String styleValue) {
        styleName = styleName.toLowerCase();
        styleValue = styleValue.toLowerCase();
        String fullstyle = styleName + ":" + styleValue;
        String shortStyle =shortStyles.get(fullstyle);
        if (shortStyle ==null) {

            String shortName = SHORTSTYLES.get(styleName);
            if (shortName == null){
                shortName=styleName;
            }
            shortStyle = shortName + styleValue.replace(" ","").replace("#","");
            shortStyles.put(fullstyle, shortStyle);
        }
        sOut.format(shortStyle + " ");


    }



    private String hex2(short i){
        if (i < 16){
            return "0" + Integer.toHexString(i);
        }else{
            return Integer.toHexString(i);
        }
    }


    private void StyleColor(String attr, Color  color) {
        int rgb = color.toArgb();

        addStyle(attr, String.format("#%1X", rgb & 0xFFFFFF));
    }







    private void fontStyle(Style style) {
        Font font = style.getFont();

        if (font.isBold())
            addStyle("font-weight", "bold");
        if (font.isItalic())
            addStyle("font-style", "italic");

        int fontheight = font.getSize();
        if (fontheight == 9) {
            //fix for stupid ol Windows
            fontheight = 10;
        }
        addStyle("font-size",fontheight + "px");

        // Font color is handled with the other colors
    }

    private Style getStyle(int rowNo, int colNo) {

        Style style = null;
        Cell cell = azquoCells.get(rowNo, colNo);
        if (cell == null) {
            style = azquoCells.getRow(rowNo).getStyle();
            if (style == null) {
                Style colStyle = azquoCells.getColumn(colNo).getStyle();
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




    private void setCellClass(int rowNo, int colNo, Cell cell, boolean hasContent) {
        Style style = getStyle(rowNo, colNo);
        int alignment = style.getHorizontalAlignment();

        if (cell != null && cell.getType() == CellValueType.IS_STRING && alignment==TextAlignmentType.GENERAL){
            alignment =TextAlignmentType.LEFT; //Excel defaults text alignment left, numbers to the right

        }

        styleOut("text-align", alignment, ALIGN);
        styleOut("vertical-align", style.getVerticalAlignment(), VERTICAL_ALIGN);
        fontStyle(style);
        Border borderRight = style.getBorders().getByBorderType(BorderType.RIGHT_BORDER);
        Border borderBottom = style.getBorders().getByBorderType(BorderType.BOTTOM_BORDER);
        if (borderBottom == null) {

            Style nextStyle = getStyle(rowNo + 1, colNo);
            borderBottom = nextStyle.getBorders().getByBorderType(BorderType.TOP_BORDER);
        }
        if (borderRight==null){
            Style nextStyle = getStyle(rowNo, colNo + 1);
            borderRight =nextStyle.getBorders().getByBorderType(BorderType.LEFT_BORDER);
        }
        if ( borderRight != null){//this is the 'blank' color
            styleOut("border-right", borderRight.getLineStyle(), BORDER);
        }
        if (borderBottom != null) {//this is the 'blank' color
            styleOut("border-bottom", borderBottom.getLineStyle(), BORDER);
        }
        borderBottomWidth = 0;
        borderRightWidth = 0;
        try {
            borderBottomWidth = (short)((BORDERSIZE.get(borderBottom.getLineStyle())).charAt(0) - 48);//tried the map as Short, Short, but had casting problems
            borderRightWidth = (short)((BORDERSIZE.get(borderRight.getLineStyle())).charAt(0) - 48);
        }catch(Exception e){
            e.printStackTrace();

        }
        if (style.isTextWrapped()){
            addStyle("word-wrap", "normal");
        }
        ConditionalFormattingResult cfr1 = cell.getConditionalFormattingResult();
        Color color = style.getForegroundColor();
        if (cfr1 !=null){
            if (cfr1.getConditionalStyle()!=null){
                style = cfr1.getConditionalStyle();
                color = style.getForegroundColor();
            }
            FormatConditionCollection cfc = cell.getFormatConditions();
            if (cfc != null) {
                for (int j = 0; j < cfc.getCount(); j++) {
                    FormatCondition fc = cfc.get(j);
                    String formula = fc.getFormula1();
                    formula = fc.getFormula2();
                    formula = fc.getFormula1();
                }
            }
        }


            StyleColor("background-color", color);
            StyleColor("color", style.getFont().getColor());
            StyleColor("border-right-color", style.getBorders().getByBorderType(BorderType.RIGHT_BORDER).getColor());
            StyleColor("border-bottom-color",  style.getBorders().getByBorderType(BorderType.BOTTOM_BORDER).getColor());

    }

    private <K> void styleOut(String attr, int key, Map<K, String> mapping) {
        String value = mapping.get(key);
        if (value != null) {
            addStyle(attr, value);
        }
    }



    private String alignStyle(Cell cell, Style style) {
        if (style.getHorizontalAlignment() == TextAlignmentType.GENERAL) {
            switch (cell.getType()) {
                case CellValueType.IS_STRING:
                    addStyle("text-align","left");
                case CellValueType.IS_BOOL:
                case CellValueType.IS_ERROR:
                    addStyle("text-align","center");
                case CellValueType.IS_NUMERIC:
                default:
                    // "right" is the default
                    break;
            }
        }
        return "";
    }




    private String loadAdminData(LoggedInConnection loggedInConnection)throws Exception{


        for (int i = 0;i < wb.getWorksheets().getNames().getCount();i++){
            com.aspose.cells.Name name = wb.getWorksheets().getNames().get(i);
            if (name.getText().toLowerCase().startsWith("az_headings") && name.getRange().getWorksheet() == azquoSheet) {
                String regionName = name.getText().substring(11).toLowerCase();
                String error = fillAdminData(loggedInConnection, regionName);
                if (error.startsWith("error:")){
                    return error;
                }
            }
        }
        return "";

    }

    public String jsonError(String data, int pos){
        int end = data.length();
        if (pos < end - 30){
            end = pos + 30;
        }
        return ("error: json parsing problem at " + data.substring(pos, end));
    }

    public String readJsonData(String data, Map<String, String> pairs){
        //should use Jackson for this,....
        /*
        final String json = "{}";
       final ObjectMapper mapper = new ObjectMapper();
       final MapType type = mapper.getTypeFactory().constructMapType(
       Map.class, String.class, Object.class);
       final Map<String, Object> data = mapper.readValue(json, type);


        */
        int pos = 1;
        if (data.charAt(pos++)!='{') return jsonError(data, pos);
        while (data.charAt(pos++) == '"') {
            int endName = data.indexOf("\"", pos);
            if (endName < 0) return jsonError(data, pos);
            String jsonName = data.substring(pos, endName);
            pos = endName + 1;
            if (data.charAt(pos++)!=':') return jsonError(data, pos);
            String jsonValue = "";
            if (data.charAt(pos)!='"'){
                int endLine = data.indexOf("}", pos);
                endName = data.indexOf(",", pos);
                if (endName < 0 || (endName > 0 && endLine < endName)) endName = endLine;
                if (endName < 0) return jsonError(data, pos);
                jsonValue = data.substring(pos, endName);
                pos = endName;
            }else {
                pos++;
                endName = data.indexOf("\"", pos);
                if (endName < 0) return jsonError(data, pos);
                jsonValue = data.substring(pos, endName);
                pos = endName + 1;
            }
            pairs.put(jsonName, jsonValue);
            if (data.charAt(pos)== '}'){
                break;
            }
            if (data.charAt(pos++)!=',') return jsonError(data, pos);
        }
        return data.substring(pos + 1);


    }

    private String evaluateExpression(String expression, Map<String, String> pairs){


        //evaluates simple text expressions
        StringBuffer sb = new StringBuffer();
        int pos= 0;
        while (pos < expression.length()){
            if (expression.charAt(pos)=='"'){
                int endQuote = expression.indexOf("\"", ++pos);
                if (endQuote < 0) return "error: expression not understood " + expression;
                sb.append(expression.substring(pos, endQuote));
                pos = endQuote + 1;
            }else if(expression.charAt(pos)=='&' || expression.charAt(pos) == ' '){
                pos++;
            }else{
                int endTerm = (expression + "&").indexOf("&", pos);
                String val = pairs.get(expression.substring(pos, endTerm).trim());
                if (val == null) return "error: expression not understood " + expression;
                sb.append(val);
                pos = endTerm;
            }
        }
        return sb.toString();
    }

    public String fillAdminData(LoggedInConnection loggedInConnection, String regionName)throws Exception{


        String data = null;
        Range headingsRange = getRange("az_headings" + regionName);
        int headingsRow = headingsRange.getFirstRow();
        int headingsCol = headingsRange.getFirstColumn();
        int firstHeading = headingsRange.getFirstColumn();
        if (regionName.equals("data") && loggedInConnection.getNamesToSearch() != null){
            Map<Set<com.azquo.memorydb.Name>,Set<Value>>shownValues = valueService.getSearchValues(loggedInConnection.getNamesToSearch());
            loggedInConnection.setValuesFound(shownValues);
            LinkedHashSet<com.azquo.memorydb.Name> nameHeadings = valueService.getHeadings(shownValues);
            int colNo = firstHeading + 1;
            int rowNo = 0;
            for (com.azquo.memorydb.Name name:nameHeadings){
                azquoCells.get(headingsRow, headingsCol + colNo).setValue(name.getDefaultDisplayName());
                colNo++;
            }
            for (Set<com.azquo.memorydb.Name>names:shownValues.keySet()) {
                rowNo++;
                colNo = firstHeading;
                azquoCells.get(headingsRow + rowNo, headingsCol + colNo).setValue(valueService.addValues(shownValues.get(names)));
                colNo++;
                for (com.azquo.memorydb.Name name : nameHeadings) {
                    for (com.azquo.memorydb.Name valueName : names) {
                        if (valueName.findTopParent() == name) {
                            azquoCells.get(headingsRow + rowNo, headingsCol + colNo).setValue(valueName.getDefaultDisplayName());
                        }
                    }
                    colNo++;
                }
            }
        }else {
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
                    String heading = headingsRange.get(0,colNo).getStringValue();
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
                            linkStart = "<a href=\"#\" onclick=";
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
                        azquoCells.get(headingsRow + rowNo, headingsCol + colNo).setStyle(headingsRange.get(0,colNo).getStyle());
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


    public String loadData(LoggedInConnection loggedInConnection) throws Exception {


        calculateAll();
        for (int i = 0;i < wb.getWorksheets().getNames().getCount();i++){
            com.aspose.cells.Name name = wb.getWorksheets().getNames().get(i);
            if (name.getText().toLowerCase().startsWith(dataRegionPrefix) && name.getRange().getWorksheet() == azquoSheet) {
                String regionName = name.getText().substring(dataRegionPrefix.length()).toLowerCase();
                String error = fillRegion(loggedInConnection, regionName);
                if (error.startsWith("error:")){
                    return error;
                }
            }
        }
        calculateAll();
        return "";
    }




    public String printAllStyles(){
        StringBuffer sb = new StringBuffer();

        for (String className : shortStyles.keySet()){
            sb.append("." + shortStyles.get(className) + " {" + className + "}\n");
        }
        return sb.toString();

    }



    private void setChoices(LoggedInConnection loggedInConnection, int reportId) {

        createChoiceMap();
        for (Cell cell : choiceMap.keySet()) {
            String choiceName = choiceMap.get(cell);
            Range choice = getRange(choiceName + "choice");
            if (choice != null) {
                UserChoice userChoice = userChoiceDAO.findForUserIdReportIdAndChoice(loggedInConnection.getUser().getId(), reportId, choiceName);
                if (userChoice!=null){
                    cell.setValue(userChoice.getChoiceValue());
                }
            }
        }
        calculateAll();
    }

    private void setSortCols(LoggedInConnection loggedInConnection, int reportId){
        for (int i = 0;i < wb.getWorksheets().getNames().getCount();i++){
            com.aspose.cells.Name name = wb.getWorksheets().getNames().get(i);
            if (name.getText().toLowerCase().startsWith(dataRegionPrefix) && name.getRange().getWorksheet() == azquoSheet) {
                String region = name.getText().substring(dataRegionPrefix.length());
                UserChoice userSortCol = userChoiceDAO.findForUserIdReportIdAndChoice(loggedInConnection.getUser().getId(), reportId, "sort " + region + " by column");
                if (userSortCol != null){
                    sortRegion = region;

                    try{
                        sortColumn=Integer.parseInt(userSortCol.getChoiceValue());
                        loggedInConnection.setSortCol(region, sortColumn);
                    }catch(Exception e){
                        //set by the system, so should not have an exception
                    }
                }

            }
        }


    }

    public String prepareSheet(LoggedInConnection loggedInConnection, int reportId) throws Exception{

        String error = "";

        calcMaxCol();
        setChoices(loggedInConnection, reportId);
        setSortCols(loggedInConnection,reportId);
        UserChoice highlight = userChoiceDAO.findForUserIdReportIdAndChoice(loggedInConnection.getUser().getId(), reportId, "highlight");
        if (highlight != null){
            try{
                highlightDays = Integer.parseInt(highlight.getChoiceValue());
            }catch(Exception e){
                //missing the highlight would not be a problem
            }
        }
        if (dataRegionPrefix.equals(azDataRegion)) {
            error = loadData(loggedInConnection);

        }else {
            //admin data only loaded on admin sheets
            error = loadAdminData(loggedInConnection);
            // still ignoring error.....
        }

        if (highlightDays>0){
            setHighlights(loggedInConnection);
        }

        //find the cells that will be used to make choices...
        calcMaxCol();
        setupColwidths();
        setupMaxHeight();
        return error;

    }


    private String createCellId(Cell cell){
        return "cell" + getUnsortRow(cell.getRow()) + "-" + cell.getColumn();
    }

    public String getCellContent(int rowNo, int colNo){
        return createCellContent(azquoCells.get(rowNo, colNo));

    }

    private String createCellContent(Cell cell) {
        String content = "";
        if (cell != null) {
            content =  cell.getStringValue();

        }
        return content;
    }



    private StringBuffer createCellClass(int rowNo, int colNo, Cell cell, boolean hasContent){
        StringBuffer cellClass = new StringBuffer();

        sOut = new Formatter(cellClass);
        setCellClass(rowNo, colNo, cell, hasContent);
        cellClass.append("rp" + rowNo + " cp" + colNo + " ");
        return cellClass;

    }


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
           if (name.getText().toLowerCase().equalsIgnoreCase(dataRegionPrefix + sortRegion) && name.getRange().getWorksheet() == azquoSheet) {
                sortRows = new HashMap<Integer, Integer>();
                unSortRows = new HashMap<Integer, Integer>();
                Range range = name.getRange();
                int colOffset = sortColumn -1;
                if (colOffset < 0) colOffset = -sortColumn -1;
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










    public StringBuffer convertToHTML(LoggedInConnection loggedInConnection){

        StringBuffer output = new StringBuffer();
        Formatter out = new Formatter(output);
        sortTheRows();


        int rowNo = 0;
        int cellTop = 0;
        String headingsRegion = null;
        Row lastRow = null;
        while (rowNo <= azquoCells.getMaxRow()) {
            List<String> rowValues = new ArrayList<String>();
            heldValues.add(rowValues);
            int sortedRowNo = getSortRow(rowNo);
            int cellLeft = 0;
            Row row = azquoCells.getRow(rowNo);
            if (row != null && !row.isHidden()) {
                if (topCell == -1){
                    topCell = rowNo;
                }
                int rowHeight = azquoCells.getRowHeightPixel(rowNo) -1;//the reduction is to cater for a border of width 1 px.

                shortStyles.put("position:absolute;top:" + cellTop + "px;height:" + rowHeight + "px", "rp" + sortedRowNo);
                int sortableStart = 0;
                int sortableEnd = 0;
                Integer sortCol = 0;
                for (int i = 0; i <= maxCol; i++) {
                    Cell cell = null;
                    if (row != lastRow) {
                        cell = azquoCells.get(sortedRowNo, i);
                    }
                    if (!azquoCells.getColumn(i).isHidden()){
                        if (headingsRegion == null){
                            SortableInfo si = sortableHeadings.get(cell);
                            if (si !=null){
                                headingsRegion = si.region;
                                sortableStart = i;
                                sortableEnd = si.lastCol;
                                sortCol = loggedInConnection.getSortCol(headingsRegion);//may be negative!
                                if (sortCol == null){
                                    sortCol = -sortableStart -1;//so that it cannot be found
                                }
                            }
                        }
                        if (leftCell == -1){
                            leftCell = i;
                        }
                        if (sortableEnd > 0 && i > sortableEnd){
                            sortableStart = 0;
                            sortableEnd = 0;
                            headingsRegion = null;
                        }
                        String attrs = "";
                        attrs = "";
                        String content = createCellContent(cell);
                        rowValues.add(content);//saved for when cells are changed
                        if (cell.getStyle().getRotation() == 90) {
                            content = "<div class=\"r90\">" + content + "</div>";
                        }

                        StringBuffer cellClass = createCellClass(sortedRowNo, i , cell, (content.length()>0));

                        int cellHeight = rowHeight;
                        int cellWidth = colWidth.get(i);
                        String sizeInfo = "";
                        if (mergedCells.get(cell) != null) {
                            CellArea mergeRange = mergedCells.get(cell);
                            for (int r=mergeRange.StartRow + 1; r <= mergeRange.EndRow; r++) {
                                cellHeight += azquoCells.getRowHeightPixel(r);

                            }
                            for (int c=mergeRange.StartColumn + 1; c <= mergeRange.EndColumn;c++) {
                                cellWidth += azquoCells.getColumnWidthPixel(c);
                            }
                            addStyle("z-index", "1");
                            //needs a background color to blot out cells underneath (probably we should not show the cells underneath)
                            addStyle("background-color", "white");
                            sizeInfo = " style=\"height:" + cellHeight + "px;width:" + cellWidth + "px;\"";
                        }
                        if (choiceMap.get(cell) != null) {
                            //create a select list
                            StringBuffer selectClass = new StringBuffer();
                            selectClass.append("select ");
                            sOut = new Formatter(selectClass);
                            addStyle("width", cellWidth + "px");
                            addStyle("height",cellHeight + "px");
                            String choiceName = choiceMap.get(cell);
                            Range choice = getRange(choiceName + "choice");
                            if (choice != null) {

                                List<com.azquo.memorydb.Name> choiceList = new ArrayList<com.azquo.memorydb.Name>();
                                String cellChoice = choice.get(0,0).getStringValue();
                                List<String> constants = new ArrayList<String>();
                                if (cellChoice.startsWith("\"")){
                                    constants = interpretList(cellChoice);


                                }else {
                                    try {
                                        String error = nameService.interpretName(loggedInConnection, choiceList, choice.get(0,0).getStringValue());
                                    } catch (Exception e) {
                                        //TODO think what to do !
                                    }
                                }
                                if (constants.size() > 0 || choiceList.size() > 0) {

                                    String origContent = content;

                                    content = "<select class = \"" + selectClass + "\" onchange=\"selectChosen('" + choiceName + "')\" id=\"" + choiceMap.get(cell) + "\" class=\"" + cellClass + "\" >\n";
                                    content += "<option value = \"\"></option>";

                                    for (String constant:constants){
                                        content += addOption(constant, origContent);
                                    }
                                    for (com.azquo.memorydb.Name name : choiceList) {
                                        if (name!=null){
                                            content += addOption(name.getDefaultDisplayName(), origContent);
                                        }
                                    }
                                    content += "</select>";

                                }else{
                                    content = "";
                                    cell.setValue("");
                                }
                            }
                        }
                        if (sortableStart > 0 && content.length() > 0){
                            if (i!=sortCol + sortableStart - 1){//sort is currently up
                                content +="<div class=\"sortup\"><a href=\"#\" onclick='sortCol(\"" + headingsRegion.trim() + "\"," + (i-sortableStart + 1) + ");'><img src=\"/images/sortup.png\"></a></div>";

                            }
                            if (i != sortableStart - sortCol - 1){
                                content +="<div class=\"sortdown\"><a href=\"#\" onclick='sortCol(\"" + headingsRegion.trim() + "\"," + (sortableStart - i - 1) + ");'><img src=\"/images/sortdown.png\"></a></div>";
                            }

                        }
                        if (content.toLowerCase().startsWith("$button;name=")){//CURRENTLY THIS ASSUMES THAT THE BUTTON LINK WILL ALWAYS BE $button;name=<name>;op=<link>
                            int nameEnd = content.substring(13).indexOf(";");
                            if (nameEnd > 0) {
                                String buttonTitle = content.substring(13, 13 + nameEnd);
                                content = "<div class=\"button\"><a href=\"#\" onclick=\"buttonPressed(" + rowNo + "," + i + ")\">" + buttonTitle + "</a></div>";
                            }
                        }
                        output.append("   <div class=\"" + cellClass + "\" " + sizeInfo + " id=\"cell" + rowNo + "-" + i + "\">" + content.trim()  + "</div>\n");
                        //out.format("    <td class=%s %s>%s</td>%n", styleName(style),
                        //        attrs, content);
                        cellLeft += colWidth.get(i);
                    }else{
                        rowValues.add(null);
                    }
                }
                cellTop += rowHeight + 1;
            }
            rowNo++;
            lastRow = row;
        }
        return output;

    }

    public StringBuffer drawCharts(LoggedInConnection loggedInConnection, String basePath)throws Exception{
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < azquoSheet.getCharts().getCount();i++){
            Chart chart = azquoSheet.getCharts().get(i);
             String name = chart.getName();
                //File tempchart = File.createTempFile(loggedInConnection.getConnectionId() + name ,".jpg");

              String tempname = (loggedInConnection.getUser().getId() + "." + loggedInConnection.getReportId() + "." + name + ".png").replace(" ","");

              chart.toImage(basePath + tempname );
              int topOffset = 0; //not sure why we should need this!
              sb.append("<div id=\"chart" + name + "\" style=\"position:absolute;top:" + (chart.getChartObject().getY() + topOffset) + "px;left:" + chart.getChartObject().getX() + "px;\"><img src=\"/api/Download?image=" + tempname + "\"/></div>\n");
         }
        return sb;
    }

    public String makeChartTitle(String region){
        String title = azquoSheet.getName();
        if (region.length() > 0){
            title += " " + region;
        }
        for (com.aspose.cells.Name name:(Set<com.aspose.cells.Name>)wb.getWorksheets().getNames()) {
            if (name.getText().toLowerCase().endsWith("chosen") && name.getRange().getWorksheet() == azquoSheet) {
                Range range= name.getRange();
                if (range!=null){
                    title += " " + range.get(0,0).getStringValue();
                }
            }
        }
        return title;
    }



    public String[] getChartHeadings(String region, String headingName){
        Range rangeFormula = getRange("az_display" + headingName + "headings" + region );

        if (rangeFormula  ==null){
            rangeFormula =  getRange("az_chart" + headingName + "headings" + region );
            if (rangeFormula == null){
                String[] error = {"error: no " + headingName + " headings for " + region};
                return error;
            }
        }
        String rangeText = rangeToText(rangeFormula);

        rangeText = rangeText.replace("\n","\t");
        return rangeText.split("\t");
    }

    public List<List<Number>> getData(String region){
        List<List<Number>> data = new ArrayList<List<Number>>();
        Range range = getRange(dataRegionPrefix + region);
        if (range == null){
            return null;
        }
        for (int row =0; row < range.getRowCount();row++){
            List<Number> rowData = new ArrayList<Number>();
            for (int col = 0;col< range.getColumnCount(); col++){
                Cell cell = range.get(row, col);
                rowData.add(cell.getDoubleValue());
            }
            data.add(rowData);
        }
        return data;
    }

    public String confirmRegion(String region){
        //look for a region name if the one given is not the correct one!
        String regionFound = null;
        for (int i = 0;i < wb.getWorksheets().getNames().getCount();i++){
            com.aspose.cells.Name name = wb.getWorksheets().getNames().get(i);
            if (name.getText().toLowerCase().startsWith(dataRegionPrefix)){
                if (regionFound != null){
                    return region;
                }
                regionFound = name.getText().substring(dataRegionPrefix.length());

            }
        }
        return regionFound;

    }
    private StringBuffer jsonValue(String name, int val){
        return jsonValue(name, val + "", true);
    }

    private StringBuffer jsonValue(String name, String value, boolean withComma){
        StringBuffer sb = new StringBuffer();
        if (withComma) sb.append(",");
        sb.append("\"" + name + "\":\"" + value + "\"");
        return sb;
    }

    private StringBuffer jsonRange(String name, String array){
        StringBuffer sb = new StringBuffer();

        sb.append(",\"" + name +"\":[");
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
                    sb.append("\"" + cell + "\"");
                }
                sb.append("]");

            }
        }
        sb.append("]");
        return sb;
    }

    private String unlockRange(Range dataRange){
        boolean firstRow = true;
        String lockMap = "";
        for (int rowNo = dataRange.getFirstRow(); rowNo < dataRange.getFirstRow() + dataRange.getRowCount();rowNo++){
            if (firstRow){
                firstRow = false;
            }else{
                lockMap += "\n";
            }
            boolean firstCol = true;
            for (int colNo = dataRange.getFirstColumn(); colNo < dataRange.getFirstColumn() + dataRange.getColumnCount(); colNo++){
                if (firstCol){
                    firstCol = false;
                }else{
                    lockMap += "\t";
                }
            }

        }
        return lockMap;

    }

    public StringBuffer getRegions(LoggedInConnection loggedInConnection,String regionNameStart){
        StringBuffer sb = new StringBuffer();
        sb.append("[");
        boolean firstRegion = true;
        for (int i = 0;i < wb.getWorksheets().getNames().getCount();i++){
            com.aspose.cells.Name name = wb.getWorksheets().getNames().get(i);
            if (name.getText().toLowerCase().startsWith(regionNameStart) && name.getRange().getWorksheet() == azquoSheet) {
                if (firstRegion){
                    firstRegion = false;
                }else{
                    sb.append(",");
                }

                Range dataRange = name.getRange();
                String lockMap = null;
                if (regionNameStart.equals(azInput) && dataRange != null){
                    lockMap = unlockRange(dataRange);
                }else{
                    lockMap = loggedInConnection.getLockMap(name.getText().substring(dataRegionPrefix.length()).toLowerCase());
                }
                sb.append("{" + jsonValue("name", name.getText().substring(regionNameStart.length()),false)
                        + jsonValue("top",dataRange.getFirstRow())
                        + jsonValue("left",dataRange.getFirstColumn())
                        + jsonValue("bottom", dataRange.getFirstRow() + dataRange.getRowCount() - 1)
                        + jsonValue("right",dataRange.getFirstColumn() + dataRange.getColumnCount() - 1)
                        + jsonRange("locks", lockMap)
                        + "}");
            }
        }
        sb.append("]");
        return sb;

    }
    private StringBuffer cellInfo(Cell cell){
        StringBuffer sb = new StringBuffer();
        String content = createCellContent(cell);
        if (content.startsWith("$button;name=")){//don't show button info
            return sb;
        }
        sb.append("{");
        sb.append(jsonValue("class", createCellClass(cell.getRow(), cell.getColumn(), cell, content.length() > 0).toString(), false));
        sb.append(jsonValue("value", content, true));
        sb.append(jsonValue("id", createCellId(cell), true));
        sb.append("}");
        return sb;
    }


    public String changeValue(int row, int col, String value, LoggedInConnection loggedInConnection) {

        row = getSortRow(row);

        Cell cellChanged = azquoCells.get(row,col);
        setCellValue(cellChanged,value);


        calculateAll();
        if (highlightDays>0){
            setHighlights(loggedInConnection);
        }


        StringBuffer sb = new StringBuffer();
        sb.append("[");
        for (int rowNo = 0; rowNo < azquoCells.getMaxRow(); rowNo++){
            int sortedRow = rowNo;
            if (unSortRows != null && unSortRows.get(rowNo) != null){
                sortedRow = unSortRows.get(rowNo);
            }
            List<String> rowValues = heldValues.get(sortedRow);
            for (int colNo = 0; colNo <= azquoCells.getMaxColumn(); colNo++){
                Cell cell = azquoCells.get(rowNo, colNo);
                if (cell!=null  && cell.getType()!= CellValueType.IS_NULL){
                    String cellVal = cell.getStringValue();

                    if (rowValues.get(colNo)!=null && !cellVal.equals(rowValues.get(colNo))) {
                        rowValues.remove(colNo);
                        rowValues.add(colNo, cellVal);
                        //setValue(loggedInConnection, cell, null); orig value needed when saving.  Provenance must now check that the value in sheet is the same as value on file
                        if (sb.length() > 1) {
                            sb.append(",");
                        }
                        sb.append(cellInfo(cell));
                    }

                }
            }
        }
        sb.append("]");
        return sb.toString();

    }

    public RegionInfo  getRegionInfo(LoggedInConnection loggedInConnection, int row, int col){
        RegionInfo regionInfo = new RegionInfo();
        row = getSortRow(row);
        for (int i = 0;i < wb.getWorksheets().getNames().getCount();i++){
            com.aspose.cells.Name name = wb.getWorksheets().getNames().get(i);
            if (name.getRange().getWorksheet() == azquoSheet) {
                Range r = name.getRange();
                if (name.getText().equalsIgnoreCase("az_HeadingsData")) {
                    //Admin inspect names only
                    regionInfo.row = row - r.getFirstRow() -1;
                    regionInfo.col = col - r.getFirstColumn();
                    if (regionInfo.row < 0) return null;
                    regionInfo.region = name.getText().toLowerCase();
                    return regionInfo;
                }else{
                    if (r!= null &&  row >=r.getFirstRow() && row < r.getFirstRow() + r.getRowCount() && col >= r.getFirstColumn() && col < r.getFirstColumn() + r.getColumnCount()){
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

    public void setValue(LoggedInConnection loggedInConnection, Cell cell, Value newValue){
        int row = cell.getRow();
        int col = cell.getColumn();
        RegionInfo regionInfo = getRegionInfo(loggedInConnection,row, col);
        if (regionInfo== null) return;
        if (regionInfo.region.equals("az_HeadingsData")){
            //Admin inspect names only
            return;
        }else{

            if (regionInfo.region.startsWith(dataRegionPrefix)){
                String region = regionInfo.region.substring(dataRegionPrefix.length());
                final List<List<List<Value>>> dataValueMap = loggedInConnection.getDataValueMap(region);
                final List<Integer> rowOrder = loggedInConnection.getRowOrder(region);


                if (dataValueMap != null && dataValueMap.get(regionInfo.row) != null) {
                    final List<List<Value>> rowValues = dataValueMap.get(rowOrder.get(regionInfo.row));
                    List<Value> newValues = new ArrayList<Value>();
                    newValues.add(newValue);
                    rowValues.set(regionInfo.col, newValues);
                }
            }
        }
        return;
    }



    public String getProvenance(LoggedInConnection loggedInConnection, int row, int col, String jsonFunction){
        RegionInfo regionInfo = getRegionInfo(loggedInConnection,row, col);
        if (regionInfo== null) return "";
        if (regionInfo.region.equals("az_headingsdata")){
            //Admin inspect names only
            for (Set<com.azquo.memorydb.Name> names:loggedInConnection.getValuesFound().keySet()) {
                if (regionInfo.row-- == 0) {
                    if (regionInfo.col == 0) {
                        List<Value> tempList = new ArrayList<Value>();
                        for (com.azquo.memorydb.Value value : loggedInConnection.getValuesFound().get(names)) {
                            tempList.add(value);
                        }
                        return valueService.formatCellProvenanceForOutput(loggedInConnection, names, tempList, jsonFunction);

                    } else {
                        for (com.azquo.memorydb.Name tempname : names) {
                            if (--regionInfo.col == 0) {
                                return valueService.formatProvenanceForOutput(tempname.getProvenance(), jsonFunction);

                            }
                        }
                    }
                }
            }
        }else{

            if (regionInfo.region.startsWith("az_displayrowheadings")){
                String region = regionInfo.region.substring(21);
                com.azquo.memorydb.Name name = loggedInConnection.getRowHeadings(region).get(regionInfo.row).get(regionInfo.col);
                if (name!=null){
                    return valueService.formatProvenanceForOutput(name.getProvenance(),jsonFunction);

                }else{
                    return "";
                }
            }else if (regionInfo.region.startsWith("az_displaycolumnheadings")){
                String region = regionInfo.region.substring(24);
                com.azquo.memorydb.Name  name = loggedInConnection.getColumnHeadings(region).get(regionInfo.col).get(regionInfo.row);//note that the array is transposed
                if (name!=null){
                    return valueService.formatProvenanceForOutput(name.getProvenance(),jsonFunction);

                }else{
                    return "";
                }
            }else if (regionInfo.region.startsWith(dataRegionPrefix)){
                String region = regionInfo.region.substring(dataRegionPrefix.length());
                return valueService.formatDataRegionProvenanceForOutput(loggedInConnection, region, regionInfo.row, regionInfo.col, jsonFunction);

            }
        }
        return "";
    }

    public String getAdminTableName(){

        //this routine assumes that the input data range will be called "az_input" + <SQL table name>
        for (int i = 0;i < wb.getWorksheets().getNames().getCount();i++){
            com.aspose.cells.Name name = wb.getWorksheets().getNames().get(i);
            if (name.getText().toLowerCase().startsWith(azInput) && name.getRange().getWorksheet() == azquoSheet) {
                return name.getText().substring(8).toLowerCase();
                //only one input range per sheet in the admin worksheet


            }
        }
        return null;
    }



    public StringBuffer getAdminData(){

        //this routine assumes that the hidden row of headings in the admin sheet will use the names from the SQL table
        for (int i = 0;i < wb.getWorksheets().getNames().getCount();i++){
            com.aspose.cells.Name name = wb.getWorksheets().getNames().get(i);
            if (name.getText().toLowerCase().startsWith(azInput) && name.getRange().getWorksheet() == azquoSheet) {
                return rangeToText(name.getRange(), true);
                //only one input range per sheet in the admin worksheet


            }
        }
        return null;
    }

    private StringBuffer rangeToText(Range range, boolean withId){
        StringBuffer sb = new StringBuffer();
        int idCol = -1;
        if (withId){
            for (int colNo = 0;colNo< range.getColumnCount();colNo++){
                Cell cell = range.getCellOrNull(0,colNo);
                if (cell!=null && cell.getStringValue().equalsIgnoreCase("id")){
                    idCol = colNo;
                    break;

                }
            }
        }
        boolean firstRow = true;
        for (int rowNo = 0; rowNo < range.getRowCount();rowNo++){
            if (range.getCellOrNull(rowNo, 0)!=null) {
                if (firstRow) {
                    firstRow = false;
                } else {
                    sb.append("\n");
                }
                boolean firstCol = true;
                if (idCol >= 0) {
                    if (firstRow) {
                        sb.append("id");

                    } else {
                        Cell cell = range.getCellOrNull(rowNo, idCol);
                        if (cell != null) {
                            sb.append(cell.getStringValue());
                        } else {
                            sb.append("0");
                        }
                    }
                    firstCol = false;

                }

                for (int colNo = 0; colNo < range.getColumnCount(); colNo++) {
                    if (firstCol) {
                        firstCol = false;
                    } else {
                        sb.append("\t");
                    }
                    Cell cell =  range.getCellOrNull(rowNo, colNo);
                    if (cell != null) {
                        sb.append(cell.getStringValue());
                    }
                }
            }
        }
        return sb;

    }

    public String saveData(LoggedInConnection loggedInConnection)throws Exception{
        for (int i = 0;i < wb.getWorksheets().getNames().getCount();i++){
            com.aspose.cells.Name name = wb.getWorksheets().getNames().get(i);
            if (name.getText().toLowerCase().startsWith(dataRegionPrefix) && name.getRange().getWorksheet() == azquoSheet) {
                String region = name.getText().substring(dataRegionPrefix.length());
                StringBuffer sb = rangeToText(name.getRange(), false);
                String result = valueService.saveData(loggedInConnection,region.toLowerCase(), sb.toString());
                if (result.startsWith("error:")){
                    return result;
                }
            }
        }
        return "";
    }


    public String executeSheet(LoggedInConnection loggedInConnection, String fileName, String spreadsheetName, int reportId) throws Exception{
        String error = "";
        try {
            wb = new Workbook(new FileInputStream(fileName));

            dataRegionPrefix = AzquoBook.azDataRegion;
            setSheet(0);
            if (spreadsheetName != null) {
                for (int i = 0; i < wb.getWorksheets().getCount(); i++){
                    Worksheet sheet = wb.getWorksheets().get(i);
                    if (sheet.getName().toLowerCase().equals(spreadsheetName.toLowerCase())) {
                        setSheet(sheet.getIndex());
                    }
                }
            }
            error = prepareSheet(loggedInConnection, reportId);
            if (error.length() == 0) {
                error = saveData(loggedInConnection);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }


        return error;

    }


    private String tabImage(int left, int right){
        return "<img alt=\"\" src = \"/images/tab" + left + right + ".png\"/>";
    }

    public String printTabs(StringBuffer tabs, String spreadsheetName){
        String error = "";
        final int tabShift = 50;
        int left = 0;
        int right = 0;
        String tabclass = null;
        for (int i = 0; i < wb.getWorksheets().getCount(); i++){
            Worksheet sheet = wb.getWorksheets().get(i);
            left = right;

            if (sheet.getName().equals(spreadsheetName)) {
                right = 1;
                tabclass="tabchosen";
            } else{
                right=2;
                tabclass ="tabbackground";
            }
            //tabs.append("<div class=\"tab\" style=\"left:" + left + "px\"><a href=\"#\" onclick=\"loadsheet('" + sheet.getSheetName() + "')\">" + sheet.getSheetName() + "</a></div>\n");
            tabs.append(tabImage(left, right) +"<span  class=\""+ tabclass + "\"><a href=\"#\" onclick=\"loadsheet('" + sheet.getName() + "')\">" + sheet.getName() + "</a></span>");
        }
        tabs.append(tabImage(right, 0));
        return error;
    }



    public void saveBookActive(HttpServletResponse response, String fileName)throws Exception{
        Workbook wbOut = new Workbook(new FileInputStream(ImportService.reportPath + "/Admin/Azquoblank.xls"));

         wbOut.combine(wb);
        if (fileName.endsWith(".xlsx")) fileName = fileName.replace(".xlsx",".xlsm");
        if (fileName.endsWith(".xls")) fileName += "m";
        response.setContentType("application/vnd.ms-excel"); // Set up mime type
        response.addHeader("Content-Disposition", "attachment; filename=" + fileName);
        OutputStream out = response.getOutputStream();
           wbOut.save(out, SaveFormat.XLSM);
        out.flush();

    }

    public void loadBook(String fileName)throws Exception{

        License license = new License();
        license.setLicense("/home/azquo/aspose/Aspose.Cells.lic");
        wb  = new Workbook(new FileInputStream(fileName));


    }


    public String convertSpreadsheetToHTML(LoggedInConnection loggedInConnection, int reportId, String spreadsheetName,StringBuffer output) throws Exception {


        setSheet(0);
        if (spreadsheetName != null) {
            for (int i = 0; i < wb.getWorksheets().getCount(); i++){
                Worksheet sheet = wb.getWorksheets().get(i);
                if (sheet.getName().toLowerCase().equals(spreadsheetName.toLowerCase())) {
                    setSheet(sheet.getIndex());
                }
            }
        }
        String error = prepareSheet(loggedInConnection, reportId);
        //TODO IGNORE ERROR CURRENTLY - SEND BACK IN MESSAGE


        output.append(convertToHTML(loggedInConnection));

        return error;
    }

    public void saveBook(String fileName)throws Exception{
        wb.save(fileName);
    }

    public void saveBook(HttpServletResponse response, String fileName)throws Exception{

        if (fileName.endsWith(".xls")) fileName += "x";
        response.setContentType("application/vnd.ms-excel"); // Set up mime type
        response.addHeader("Content-Disposition", "attachment; filename=" + fileName);
        OutputStream out = response.getOutputStream();
        wb.save(out, SaveFormat.XLSX);
        out.flush();

    }

    public int getNumberOfSheets(){
        return wb.getWorksheets().getCount();
    }

    public String convertSheetToCSV(final String tempFileName, final int sheetNo) throws Exception{
        Row row;
        Cell cell;
        Worksheet sheet = wb.getWorksheets().get(sheetNo);
        Cells cells = sheet.getCells();
        String fileType = sheet.getName();

        int rows; // No of rows
        rows = cells.getMaxRow();

          int maxCol = cells.getMaxColumn();

        // This trick ensures that we get the data properly even if it doesn't start from first few rows
        File temp = File.createTempFile(tempFileName.substring(0, tempFileName.length() - 4), "." + fileType);
        String tempName = temp.getPath();

        temp.deleteOnExit();
        FileWriter fw = new FileWriter(tempName);
        BufferedWriter bw = new BufferedWriter(fw);

        for (int r = 0; r <= rows; r++) {
            row = cells.getRow(r);
            if (row != null) {
                //System.out.println("Excel row " + r);
                int colCount = 0;
                for (int c = 0; c <= maxCol; c++) {
                     cell = cells.get(r,c);
                    if (colCount++ > 0) bw.write('\t');
                    if (cell != null && cell.getType()!= CellValueType.IS_NULL) {

                        String cellFormat = "";
                        cellFormat = cell.getStringValue();
                        //Integers seem to have '.0' appended, so this is a manual chop.  It might cause problems if someone wanted to import a version '1.0'
                        bw.write(cellFormat);

                    }

                }
                bw.write('\n');
            }
        }
        bw.close();
        return tempName;

    }




    public String getReportName(){
        for (int i = 0;i < wb.getWorksheets().getNames().getCount();i++){
            com.aspose.cells.Name name = wb.getWorksheets().getNames().get(i);
            if (name.getText().equalsIgnoreCase("az_reportname")){
                return getRangeValue(name.getText());
            }
        }
        return null;

    }









}
