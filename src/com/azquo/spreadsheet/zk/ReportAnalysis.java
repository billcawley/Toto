package com.azquo.spreadsheet.zk;

import com.azquo.StringLiterals;
import com.azquo.admin.onlinereport.OnlineReport;
import com.azquo.dataimport.ImportService;
import com.azquo.spreadsheet.LoggedInUser;
import com.azquo.spreadsheet.SpreadsheetService;
import org.zkoss.poi.hssf.usermodel.HSSFFont;
import org.zkoss.poi.hssf.util.HSSFColor;
import org.zkoss.poi.ss.usermodel.*;
import io.keikai.api.Importers;
import io.keikai.api.model.Book;
import io.keikai.model.CellRegion;
import io.keikai.model.SCell;
import io.keikai.model.SName;
import io.keikai.model.SSheet;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class ReportAnalysis {

    public static int analyseReport(LoggedInUser loggedInUser, OnlineReport or, int rowIndex, Sheet sheet) throws IOException {
        String bookPath = SpreadsheetService.getHomeDir() + ImportService.dbPath + loggedInUser.getBusinessDirectory() + ImportService.onlineReportsDir + or.getFilenameForDisk();
        Book book = Importers.getImporter().imports(new File(bookPath), "Report name");
        Map<String, List<SName>> azquoNames = new HashMap<>();
        List<SName> otherNames = new ArrayList<>();
        List<SName> expectedSingleNames = new ArrayList<>();
        for (SName name : book.getInternalBook().getNames()) {
            if (isSingleAzquoName(name)) {
                expectedSingleNames.add(name);
            } else {
                String azquoName = getAzquoName(name);
                if (azquoName != null) {
                    azquoNames.computeIfAbsent(azquoName, s -> new ArrayList<>()).add(name);
                } else {
                    otherNames.add(name);
                }
            }
        }
        // copying the pattern in PendingUploadController
        int cellIndex = 0;
        Row row;
        if (!expectedSingleNames.isEmpty()) {
            row = sheet.createRow(rowIndex++);
            Cell cell1 = row.createCell(cellIndex);

            cell1.setCellValue("Expected Single Azquo Names");
            CellStyle style = sheet.getWorkbook().createCellStyle();
            Font font = sheet.getWorkbook().createFont();
            font.setBoldweight(HSSFFont.BOLDWEIGHT_BOLD);
            //font.setFontHeightInPoints((short)(20*20));
            style.setFont(font);
            cell1.setCellStyle(style);


            cellIndex = 0;
            row = sheet.createRow(rowIndex++);
            for (SName name : expectedSingleNames) {
                row.createCell(cellIndex++).setCellValue(name.getName());
                row.createCell(cellIndex++).setCellValue(name.getRefersToSheetName());
                if (!name.getName().toLowerCase().startsWith("az_data") && !name.getName().toLowerCase().startsWith("az_display")){
                    List<List<String>> lists = nameToStringLists(name);
                    if (lists.size() == 1 && lists.get(0).size() == 1) {
                        row.createCell(cellIndex).setCellValue(lists.get(0).get(0));
                        row = sheet.createRow(rowIndex++);
                    } else { // ok things get a bit more complex, basically got to move the area into the analysis sheet
                        for (List<String> contentsRow : lists){
                            int startCol = cellIndex;
                            for (String cell : contentsRow){
                                row.createCell(startCol++).setCellValue(cell);
                            }
                            row = sheet.createRow(rowIndex++);
                        }
                    }
                }
                cellIndex = 0;
            }
        }

        if (!azquoNames.isEmpty()) {
            row = sheet.createRow(rowIndex++);
            Cell cell2 = row.createCell(cellIndex);

            cell2.setCellValue("Azquo Names");
            CellStyle style = sheet.getWorkbook().createCellStyle();
            Font font = sheet.getWorkbook().createFont();
            font.setBoldweight(HSSFFont.BOLDWEIGHT_BOLD);
            //font.setFontHeightInPoints((short)(20*20));
            style.setFont(font);
            cell2.setCellStyle(style);

            cellIndex = 0;
            row = sheet.createRow(rowIndex++);
            List<SName> leftOver = new ArrayList<>();
            for (String azName : azquoNames.keySet()) {
                if (azquoNames.get(azName).size() == 1) {
                    leftOver.addAll(azquoNames.get(azName));
                } else {
                    Cell cell1 = row.createCell(cellIndex);
                    cell1.setCellValue((azName.isEmpty() ? "[BLANK]" : azName));

                    style = sheet.getWorkbook().createCellStyle();
                    font = sheet.getWorkbook().createFont();
                    font.setBoldweight(HSSFFont.BOLDWEIGHT_BOLD);
                    font.setColor(HSSFColor.GREEN.index);
                    style.setFont(font);
                    cell1.setCellStyle(style);

                    cellIndex = 0;
                    row = sheet.createRow(rowIndex++);
                    for (SName name : azquoNames.get(azName)) {
                        row.createCell(cellIndex++).setCellValue(name.getName());
                        row.createCell(cellIndex++).setCellValue(name.getRefersToSheetName());
                        if (!name.getName().toLowerCase().startsWith("az_data") && !name.getName().toLowerCase().startsWith("az_display")){
                            List<List<String>> lists = nameToStringLists(name);
                            if (lists.size() == 1 && lists.get(0).size() == 1) {
                                row.createCell(cellIndex).setCellValue(lists.get(0).get(0));
                                row = sheet.createRow(rowIndex++);
                            } else { // ok things get a bit more complex, basically got to move the area into the analysis sheet
                                for (List<String> contentsRow : lists){
                                    int startCol = cellIndex;
                                    for (String cell : contentsRow){
                                        row.createCell(startCol++).setCellValue(cell);
                                    }
                                    row = sheet.createRow(rowIndex++);
                                }
                            }
                        } else {
                            row = sheet.createRow(rowIndex++);
                        }
                        cellIndex = 0;
                    }
                }
            }
            if (!leftOver.isEmpty()) {
                row = sheet.createRow(rowIndex++);
                Cell cell1 = row.createCell(cellIndex);
                cell1.setCellValue("Ungrouped");
                style = sheet.getWorkbook().createCellStyle();
                font = sheet.getWorkbook().createFont();
                font.setColor(HSSFColor.RED.index);
                font.setBoldweight(HSSFFont.BOLDWEIGHT_BOLD);
                style.setFont(font);
                cell1.setCellStyle(style);
                cellIndex = 0;
                row = sheet.createRow(rowIndex++);
                for (SName name : leftOver) {
                    row.createCell(cellIndex++).setCellValue(name.getName());
                    row.createCell(cellIndex++).setCellValue(name.getRefersToSheetName());
                    if (!name.getName().toLowerCase().startsWith("az_data") && !name.getName().toLowerCase().startsWith("az_display")){
                        List<List<String>> lists = nameToStringLists(name);
                        if (lists.size() == 1 && lists.get(0).size() == 1) {
                            row.createCell(cellIndex).setCellValue(lists.get(0).get(0));
                            row = sheet.createRow(rowIndex++);
                        } else { // ok things get a bit more complex, basically got to move the area into the analysis sheet
                            for (List<String> contentsRow : lists){
                                int startCol = cellIndex;
                                for (String cell : contentsRow){
                                    row.createCell(startCol++).setCellValue(cell);
                                }
                                row = sheet.createRow(rowIndex++);
                            }
                        }
                    } else {
                        row = sheet.createRow(rowIndex++);
                    }
                    cellIndex = 0;
                }
            }
        }
        if (!otherNames.isEmpty()) {
            row = sheet.createRow(rowIndex++);
            Cell cell1 = row.createCell(cellIndex);

            cell1.setCellValue("Other names");
            CellStyle style = sheet.getWorkbook().createCellStyle();
            Font font = sheet.getWorkbook().createFont();
            font.setBoldweight(HSSFFont.BOLDWEIGHT_BOLD);
            //font.setFontHeightInPoints((short)(20*20));
            style.setFont(font);
            cell1.setCellStyle(style);

            cellIndex = 0;
            row = sheet.createRow(rowIndex++);
            for (SName name : otherNames) {
                row.createCell(cellIndex++).setCellValue(name.getName());
                row.createCell(cellIndex++).setCellValue(name.getRefersToSheetName());
                if (!name.getName().toLowerCase().startsWith("az_data") && !name.getName().toLowerCase().startsWith("az_display")){
                    List<List<String>> lists = nameToStringLists(name);
                    if (lists.size() == 1 && lists.get(0).size() == 1) {
                        row.createCell(cellIndex).setCellValue(lists.get(0).get(0));
                        row = sheet.createRow(rowIndex++);
                    } else { // ok things get a bit more complex, basically got to move the area into the analysis sheet
                        for (List<String> contentsRow : lists){
                            int startCol = cellIndex;
                            for (String cell : contentsRow){
                                row.createCell(startCol++).setCellValue(cell);
                            }
                            row = sheet.createRow(rowIndex++);
                        }
                    }
                } else {
                    row = sheet.createRow(rowIndex++);
                }
                cellIndex = 0;
            }
        }
        return rowIndex;
    }

    // so a type of name that appears once e.g. az_reportName
    static boolean isSingleAzquoName(SName sName) {
        String name = sName.getName().toLowerCase();
        if (name.equals(StringLiterals.AZREPORTNAME)) return true;
        if (name.equalsIgnoreCase(ReportExecutor.EXECUTERESULTS)) return true;
        if (name.equalsIgnoreCase(ReportExecutor.OUTCOME)) return true;
        if (name.equalsIgnoreCase(ReportExecutor.SYSTEMDATA)) return true;
        if (name.equalsIgnoreCase(ReportExecutor.EXPORT)) return true;
        if (name.equalsIgnoreCase(StringLiterals.EXECUTE)) return true;
        if (name.equalsIgnoreCase(StringLiterals.PREEXECUTE)) return true;
        if (name.equalsIgnoreCase(StringLiterals.AZCURRENTUSER)) return true;
        if (name.startsWith(StringLiterals.FOLLOWON)) return true;
        // queries considered one offs
        if (name.endsWith("query")) return true;
        return false;
    }

    static String getAzquoName(SName sName) {
        String name = sName.getName().toLowerCase();

        if (name.startsWith(StringLiterals.AZDATAREGION)) return name.substring(StringLiterals.AZDATAREGION.length());
        if (name.startsWith(StringLiterals.AZLISTSTART)) return name.substring(StringLiterals.AZLISTSTART.length());
        if (name.startsWith(StringLiterals.AZDISPLAYROWHEADINGS))
            return name.substring(StringLiterals.AZDISPLAYROWHEADINGS.length());
        if (name.startsWith(StringLiterals.AZDISPLAYCOLUMNHEADINGS))
            return name.substring(StringLiterals.AZDISPLAYCOLUMNHEADINGS.length());
        if (name.startsWith(StringLiterals.AZDISPLAY)) return name.substring(StringLiterals.AZDISPLAY.length());
        if (name.startsWith(StringLiterals.AZDRILLDOWN)) return name.substring(StringLiterals.AZDRILLDOWN.length());
        if (name.startsWith(StringLiterals.AZOPTIONS)) return name.substring(StringLiterals.AZOPTIONS.length());
        if (name.startsWith(StringLiterals.AZREPEATREGION))
            return name.substring(StringLiterals.AZREPEATREGION.length());
        if (name.startsWith(StringLiterals.AZREPEATSCOPE)) return name.substring(StringLiterals.AZREPEATSCOPE.length());
        if (name.startsWith(StringLiterals.AZREPEATITEM)) return name.substring(StringLiterals.AZREPEATITEM.length());
        if (name.startsWith(StringLiterals.AZREPEATLIST)) return name.substring(StringLiterals.AZREPEATLIST.length());
        if (name.startsWith(StringLiterals.AZCOLUMNHEADINGS))
            return name.substring(StringLiterals.AZCOLUMNHEADINGS.length());
        if (name.startsWith(StringLiterals.AZROWHEADINGS)) return name.substring(StringLiterals.AZROWHEADINGS.length());
        if (name.startsWith(StringLiterals.AZXMLEXTRAINFO))
            return name.substring(StringLiterals.AZXMLEXTRAINFO.length());
        if (name.startsWith(StringLiterals.AZXMLFILENAME)) return name.substring(StringLiterals.AZXMLFILENAME.length());
        if (name.startsWith(StringLiterals.AZXMLFLAG)) return name.substring(StringLiterals.AZXMLFLAG.length());
        if (name.startsWith(StringLiterals.AZXML)) return name.substring(StringLiterals.AZXML.length());
        if (name.startsWith(StringLiterals.AZSUPPORTREPORTNAME))
            return name.substring(StringLiterals.AZSUPPORTREPORTNAME.length());
        if (name.startsWith(StringLiterals.AZSUPPORTREPORTFILEXMLTAG))
            return name.substring(StringLiterals.AZSUPPORTREPORTFILEXMLTAG.length());
        if (name.startsWith(StringLiterals.AZSUPPORTREPORTSELECTIONS))
            return name.substring(StringLiterals.AZSUPPORTREPORTSELECTIONS.length());
        if (name.startsWith(StringLiterals.AZCONTEXT)) return name.substring(StringLiterals.AZCONTEXT.length());
        if (name.startsWith(StringLiterals.AZPIVOTFILTERS))
            return name.substring(StringLiterals.AZPIVOTFILTERS.length());
        if (name.startsWith(StringLiterals.AZCONTEXTFILTERS))
            return name.substring(StringLiterals.AZCONTEXTFILTERS.length());
        if (name.startsWith(StringLiterals.AZCONTEXTHEADINGS))
            return name.substring(StringLiterals.AZCONTEXTHEADINGS.length());
        if (name.startsWith(StringLiterals.AZPIVOTHEADINGS))
            return name.substring(StringLiterals.AZPIVOTHEADINGS.length());
        if (name.startsWith(StringLiterals.AZSAVE)) return name.substring(StringLiterals.AZSAVE.length());
        if (name.startsWith(StringLiterals.AZREPEATSHEET)) return name.substring(StringLiterals.AZREPEATSHEET.length());
        if (name.startsWith(StringLiterals.AZPDF)) return name.substring(StringLiterals.AZPDF.length());
        if (name.startsWith(StringLiterals.AZTOTALFORMAT)) return name.substring(StringLiterals.AZTOTALFORMAT.length());
        if (name.startsWith(StringLiterals.AZFASTLOAD)) return name.substring(StringLiterals.AZFASTLOAD.length());
        if (name.startsWith(ZKComposer.AZSHEETOPTIONS)) return name.substring(ZKComposer.AZSHEETOPTIONS.length());
        if (name.startsWith(StringLiterals.AZEMAILADDRESS))
            return name.substring(StringLiterals.AZEMAILADDRESS.length());
        if (name.startsWith(StringLiterals.AZEMAILSUBJECT))
            return name.substring(StringLiterals.AZEMAILSUBJECT.length());
        if (name.startsWith(StringLiterals.AZEMAILTEXT)) return name.substring(StringLiterals.AZEMAILTEXT.length());
        if (name.startsWith(StringLiterals.AZFILETYPE)) return name.substring(StringLiterals.AZFILETYPE.length());
        if (name.startsWith("az_query")) return name.substring("az_query".length());

        // damn string literals!
        if (name.endsWith("choice")) return name.substring(0, name.indexOf("choice"));
        if (name.endsWith("chosen")) return name.substring(0, name.indexOf("chosen"));
        if (name.endsWith("multi")) return name.substring(0, name.indexOf("multi"));

        return null;
    }

    // stripped down version from BookUtils - it should show the formula not the result
    private static List<List<String>> nameToStringLists(SName sName) {
        List<List<String>> toReturn = new ArrayList<>();
        CellRegion region = sName.getRefersToCellRegion();
        if (region == null) return toReturn;
        SSheet sheet = sName.getBook().getSheetByName(sName.getRefersToSheetName());
        for (int rowIndex = region.getRow(); rowIndex <= region.getLastRow(); rowIndex++) {
            List<String> row = new ArrayList<>();
            toReturn.add(row);
            for (int colIndex = region.getColumn(); colIndex <= region.getLastColumn(); colIndex++) {
                SCell cell = sheet.getCell(rowIndex, colIndex);
                if (cell != null) {
                    if (!cell.getType().equals(SCell.CellType.BLANK)) {
                        if (cell.getType().equals(SCell.CellType.FORMULA)) {
                            row.add(cell.getFormulaValue());
                        } else if (cell.getType().equals(SCell.CellType.NUMBER)) {
                            row.add(cell.getNumberValue() + "");
                        } else {
                            row.add(cell.getStringValue());
                        }
                    } else {
                        row.add("");
                    }
                }
            }
            boolean blank = true;
            for (String cell : row){
                if (!cell.trim().isEmpty()){
                    blank = false;
                    break;
                }
            }
            if (blank){
                toReturn.remove(row);
            }
        }
        return toReturn;
    }
}
