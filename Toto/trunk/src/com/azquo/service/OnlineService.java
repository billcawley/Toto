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


    public String readExcel(LoggedInConnection loggedInConnection, OnlineReport onlineReport) {


        StringBuffer sb = new StringBuffer();
        StringBuffer head = new StringBuffer();
        AzquoBook azquoBook = new AzquoBook();
        try {
            Workbook wb  = WorkbookFactory.create(new FileInputStream(onlineReport.getFilename()));
            azquoBook.setWb(wb);
            printHeadStart(loggedInConnection, onlineReport.getId(), head);
            printBody(loggedInConnection, onlineReport.getId(), azquoBook);
        } catch (Exception e) {
            e.printStackTrace();
        }
        head.append("<style>\n");
        head.append(azquoBook.printAllStyles());
        printFile("excelStyle.css");
        head.append("</style>\n");
        head.append("<script>\n");
        printFile("online.js");
         head.append("</script>");
        return head.toString() + "</head>\n" + sb.toString();
    }

    public void printHeadStart(LoggedInConnection loggedInConnection, int reportId, StringBuffer head) throws Exception {


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

    }


    public void printBody(LoggedInConnection loggedInConnection, int reportId, AzquoBook azquoBook, StringBuffer output) throws Exception {


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
                        output.append("   <div class=\"" + cellClass + "\"  id=\"cell" + rowNo + "-" + i + "\"> " + content  + "</div>\n");
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