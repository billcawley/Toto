package com.azquo.dataimport;

import org.apache.commons.lang.math.NumberUtils;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;
import org.zkoss.poi.hssf.usermodel.HSSFDateUtil;
import org.zkoss.poi.ss.format.CellDateFormatter;
import org.zkoss.poi.ss.usermodel.BuiltinFormats;
import org.zkoss.poi.ss.usermodel.DataFormatter;
import org.zkoss.poi.ss.util.CellReference;
import org.zkoss.poi.xssf.eventusermodel.ReadOnlySharedStringsTable;
import org.zkoss.poi.xssf.model.StylesTable;
import org.zkoss.poi.xssf.usermodel.XSSFCellStyle;
import org.zkoss.poi.xssf.usermodel.XSSFRichTextString;

import java.util.Locale;
import java.util.Map;

// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//
// as mentioned initially decompiled from apache. It's about to be modified a lot but maybe put a note at the top about the Apache license? todo

// various quirks about how to deal with white space are to match previous POI Workbook behavior
public class XSSFSheetXMLHandler extends DefaultHandler {
    private StylesTable stylesTable;
    private ReadOnlySharedStringsTable sharedStringsTable;
    private final POIEventDataRecipient poiEventDataRecipient;
    private boolean vIsOpen;
    private boolean fIsOpen;
    private boolean isIsOpen;
    private XssfDataType nextDataType;
    private short formatIndex;
    private String formatString;
    private final DataFormatter formatter;
    private String cellRef;
    private boolean formulasNotResults;
    private StringBuffer value;
    private StringBuffer formula;
    int cellIndex;
    int rowIndex;
    private final Map<String, Integer> mergesByRowMap;

    public XSSFSheetXMLHandler(StylesTable styles, ReadOnlySharedStringsTable strings, POIEventDataRecipient poiEventDataRecipient, DataFormatter dataFormatter, boolean formulasNotResults, Map<String, Integer> mergesByRowMap) {
        rowIndex = -1;
        this.value = new StringBuffer();
        this.formula = new StringBuffer();
        this.stylesTable = styles;
        this.sharedStringsTable = strings;
        this.poiEventDataRecipient = poiEventDataRecipient;
        this.formulasNotResults = formulasNotResults;
        this.nextDataType = XssfDataType.NUMBER;
        this.formatter = dataFormatter;
        cellIndex = -1;
        this.mergesByRowMap = mergesByRowMap;
    }

    private boolean isTextTag(String name) {
        if ("v".equals(name)) {
            return true;
        } else if ("inlineStr".equals(name)) {
            return true;
        } else {
            return "t".equals(name) && this.isIsOpen;
        }
    }

    public void startElement(String uri, String localName, String name, Attributes attributes) throws SAXException {
        if (this.isTextTag(name)) {
            this.vIsOpen = true;
            this.value.setLength(0);
        } else if ("is".equals(name)) {
            this.isIsOpen = true;
        } else {
            String cellType;
            String cellStyleStr;
            if ("f".equals(name)) {
                this.formula.setLength(0);
                if (this.nextDataType == XssfDataType.NUMBER) {
                    this.nextDataType = XssfDataType.FORMULA;
                }

                cellType = attributes.getValue("t");
                if (cellType != null && cellType.equals("shared")) {
                    cellStyleStr = attributes.getValue("ref");
                    if (cellStyleStr != null) {
                        this.fIsOpen = true;
                    } else if (this.formulasNotResults) {
                        System.err.println("Warning - shared formulas not yet supported!");
                    }
                } else {
                    this.fIsOpen = true;
                }
            } else if ("row".equals(name)) {
                // startrow
                // clear the cell ref - if the row is completely empty we don't want an old one picked up on row end
                this.cellRef = null;
                int rowNum = Integer.parseInt(attributes.getValue("r")) - 1;
                if (++rowIndex != rowNum) {
                    while (rowIndex != rowNum) {
                            poiEventDataRecipient.endRow();
                        rowIndex++;
                    }
                }
                cellIndex = -1;
            } else if ("c".equals(name)) {
                // todo - are there ever horizontal gaps? e.g. a cell starting at B. If not could just increment a cell index here, more efficient than looking it up off the cell ref
                this.nextDataType = XssfDataType.NUMBER;
                this.formatIndex = -1;
                this.formatString = null;
                this.cellRef = attributes.getValue("r");
                cellType = attributes.getValue("t");
                cellStyleStr = attributes.getValue("s");
                if ("b".equals(cellType)) {
                    this.nextDataType = XssfDataType.BOOLEAN;
                } else if ("e".equals(cellType)) {
                    this.nextDataType = XssfDataType.ERROR;
                } else if ("inlineStr".equals(cellType)) {
                    this.nextDataType = XssfDataType.INLINE_STRING;
                } else if ("s".equals(cellType)) {
                    this.nextDataType = XssfDataType.SST_STRING;
                } else if ("str".equals(cellType)) {
                    this.nextDataType = XssfDataType.FORMULA;
                } else if (cellStyleStr != null) {
                    int styleIndex = Integer.parseInt(cellStyleStr);
                    XSSFCellStyle style = this.stylesTable.getStyleAt(styleIndex);
                    this.formatIndex = style.getDataFormat();
                    this.formatString = style.getDataFormatString();
                    if (this.formatString == null) {
                        this.formatString = BuiltinFormats.getBuiltinFormat(this.formatIndex);
                    }
                }
            }
        }
    }

    public void endElement(String uri, String localName, String name) throws SAXException {
        String thisStr = null;
        if (this.isTextTag(name)) {
            this.vIsOpen = false;
            switch (this.nextDataType) {
                case BOOLEAN:
                    char first = this.value.charAt(0);
                    thisStr = first == '0' ? "FALSE" : "TRUE";
                    break;
                case ERROR:
                    thisStr = "ERROR:" + this.value.toString();
                    break;
                case FORMULA:
                    if (this.formulasNotResults) {
                        thisStr = this.formula.toString();
                    } else {
                        String fv = this.value.toString();
                        if (this.formatString != null) {
                            try {
                                double d = Double.parseDouble(fv);
                                thisStr = this.formatter.formatRawCellContents(d, this.formatIndex, this.formatString);
                            } catch (NumberFormatException var11) {
                                thisStr = fv;
                            }
                        } else {
                            thisStr = fv;
                        }
                    }
                    break;
                case INLINE_STRING:
                    XSSFRichTextString rtsi = new XSSFRichTextString(this.value.toString());
                    thisStr = rtsi.toString();
                    break;
                case SST_STRING:
                    String sstIndex = this.value.toString();

                    try {
                        int idx = Integer.parseInt(sstIndex);
                        XSSFRichTextString rtss = new XSSFRichTextString(this.sharedStringsTable.getEntryAt(idx));
                        thisStr = rtss.toString();
                    } catch (NumberFormatException var10) {
                        System.err.println("Failed to parse SST index '" + sstIndex + "': " + var10.toString());
                    }
                    break;
                case NUMBER:
                    String n = this.value.toString();
                    if (this.formatString != null) {
                        thisStr = this.formatter.formatRawCellContents(Double.parseDouble(n), this.formatIndex, this.formatString);
                    } else {
                        thisStr = n;
                    }
                    break;
                default:
                    thisStr = "(TODO: Unexpected type: " + this.nextDataType + ")";
            }
                // e.g. AB41
                int numberIndex = 0;
                while (numberIndex < cellRef.length()) {
                    char ch = cellRef.charAt(numberIndex);
                    if (Character.isDigit(ch)) {
                        break;
                    }
                    numberIndex++;
                }
                int colIndex = CellReference.convertColStringToIndex(cellRef.substring(0, numberIndex));
                if (++cellIndex < colIndex) { // was != but < safer - the merge code below could cause problems
                    while (cellIndex != colIndex) {
                        poiEventDataRecipient.cellData("");
                        cellIndex++;
                    }
                }
                // NOW, we need a third version of get cell value here!
                //this.nextDataType
                String ourValue = getCellValue(this.value.toString(), thisStr, nextDataType, formatString, formatIndex);
                poiEventDataRecipient.cellData(ourValue);
                if (mergesByRowMap != null && mergesByRowMap.get(cellRef) != null && !ourValue.isEmpty()){
                    int extraCells = mergesByRowMap.get(cellRef);
                    for (int i = 0; i < extraCells; i++){
                        cellIndex++;
                        poiEventDataRecipient.cellData(ourValue);
                    }
                }

        } else if ("f".equals(name)) {
            this.fIsOpen = false;
        } else if ("is".equals(name)) {
            this.isIsOpen = false;
        } else if ("row".equals(name)) {
                if (cellRef != null){ // it will be null if the row was completely empty
                    // fill with blanks to the end to match Excel - grab the most recent col index which may well have been blank and hence ignored above
                    // e.g. AB41
                    int numberIndex = 0;
                    while (numberIndex < cellRef.length()) {
                        char ch = cellRef.charAt(numberIndex);
                        if (Character.isDigit(ch)) {
                            break;
                        }
                        numberIndex++;
                    }
                    int colIndex = CellReference.convertColStringToIndex(cellRef.substring(0, numberIndex));
                    // don't increment cell index initially, this is the end of the row . . .
                    if (cellIndex != colIndex) {
                        while (cellIndex != colIndex) {
                            poiEventDataRecipient.cellData("");
                            cellIndex++;
                        }
                    }
                }
                poiEventDataRecipient.endRow();
        }
    }

    public void characters(char[] ch, int start, int length) throws SAXException {
        if (this.vIsOpen) {
            this.value.append(ch, start, length);
        }

        if (this.fIsOpen) {
            this.formula.append(ch, start, length);
        }

    }

    enum XssfDataType {
        BOOLEAN,
        ERROR,
        FORMULA,
        INLINE_STRING,
        SST_STRING,
        NUMBER;
    }

    // POI event driven version, used when converting sheets to csv. There should be some factoring at some point

    static String getCellValue(String plainValue, String formattedValue, XSSFSheetXMLHandler.XssfDataType xssfDataType, String formatString, int formatIndex) {
        String returnString = "";
        if (xssfDataType == XSSFSheetXMLHandler.XssfDataType.INLINE_STRING || xssfDataType == XSSFSheetXMLHandler.XssfDataType.SST_STRING || (xssfDataType == XSSFSheetXMLHandler.XssfDataType.FORMULA && formatString == null)) {
            returnString = formattedValue;
        } else if (xssfDataType == XSSFSheetXMLHandler.XssfDataType.NUMBER || (xssfDataType == XSSFSheetXMLHandler.XssfDataType.FORMULA && NumberUtils.isNumber(plainValue))) { // was checking that the formula was numeric on the previous one but not necessary here, I think checking formatString as null abov does it
            // first we try to get it without locale - better match on built in formats it seems, this is based on problems getting Sep 18 as opposed to Sep-18, we want the latter
            String dataFormat = BuiltinFormats.getBuiltinFormat(formatIndex);
            if (dataFormat != null) {
                formatString = dataFormat;
            }

            if (formatString == null) {
                formatString = "";
            }
            Double returnNumber = Double.parseDouble(plainValue);
            returnString = returnNumber.toString();
            if (returnString.contains("E")) {
                returnString = String.format("%f", returnNumber);
            }
            if (returnNumber % 1 == 0) {
                // specific condition - integer and format all 000, then actually use the format. For zip codes
                if (formatString.length() > 1 && formatString.contains("0") && formatString.replace("0", "").isEmpty()) {
                    returnString = formattedValue;
                } else {
                    returnString = returnNumber.longValue() + "";
                }
            }
            if (formatString.equals("h:mm") && returnString.length() == 4) {
                //ZK BUG - reads "hh:mm" as "h:mm"
                returnString = "0" + returnString;
            } else {
                if (formatString.toLowerCase().contains("m")) {
                    if (formatString.length() > 6) {
                        try {
                            returnString = ImportService.YYYYMMDD.format(HSSFDateUtil.getJavaDate(returnNumber));
                        } catch (Exception e) {
                            //not sure what to do here.
                        }
                    } else { // it's still a date - match the defauilt format
                        // this seems to be required as if the date is based off another cell then the normal formatter will return the formula
                        CellDateFormatter cdf = new CellDateFormatter(formatString, Locale.UK);
                        returnString = cdf.format(HSSFDateUtil.getJavaDate(returnNumber));
                    }
                }
            }
        } else if (xssfDataType == XSSFSheetXMLHandler.XssfDataType.BOOLEAN /* damn, how to deal?? || (cell.getCellType() == Cell.CELL_TYPE_FORMULA && cell.getCachedFormulaResultType() == Cell.CELL_TYPE_BOOLEAN) */) {
            returnString = formattedValue;
        }/* else if (cell.getCellType() != Cell.CELL_TYPE_BLANK) {
            if (cell.getCellType() == Cell.CELL_TYPE_FORMULA) {
                System.out.println("other forumla cell type : " + cell.getCachedFormulaResultType());
            }
            System.out.println("other cell type : " + cell.getCellType());
        }*/
        if (returnString.contains("\"\"") && returnString.startsWith("\"") && returnString.endsWith("\"")) {
            //remove spurious quote marks
            returnString = returnString.substring(1, returnString.length() - 1).replace("\"\"", "\"");
        }
        if (returnString.startsWith("`") && returnString.indexOf("`", 1) < 0) {
            returnString = returnString.substring(1);
        }
        if (returnString.startsWith("'") && returnString.indexOf("'", 1) < 0)
            returnString = returnString.substring(1);//in Excel some cells are preceded by a ' to indicate that they should be handled as strings
/*
        // Deal with merged cells, not sure if there's a performance issue here? If a heading spans successive cells need to have the span value
        if (returnString.isEmpty() && cell.getSheet().getNumMergedRegions() > 0) {
            int rowIndex = cell.getRowIndex();
            int cellIndex = cell.getColumnIndex();
            for (int i = 0; i < cell.getSheet().getNumMergedRegions(); i++) {
                CellRangeAddress region = cell.getSheet().getMergedRegion(i); //Region of merged cells
                //check first cell of the region
                if (rowIndex == region.getFirstRow() && // logic change - only do the merge thing on the first column
                        cellIndex > region.getFirstColumn() // greater than, we're only interested if not the first column
                        && cellIndex <= region.getLastColumn()
                ) {
                    returnString = getCellValue(cell.getSheet().getRow(region.getFirstRow()).getCell(region.getFirstColumn()));
                }
            }
        }*/
        return returnString.trim();
    }


}