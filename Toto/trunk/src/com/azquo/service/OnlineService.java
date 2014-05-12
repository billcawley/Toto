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

import javax.servlet.http.HttpServletResponse;
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
        loggedInConnection.setAzquoBook(azquoBook);
        String output = readFile("onlineReport.html").toString();
        try {
            Workbook wb  = WorkbookFactory.create(new FileInputStream(onlineReport.getFilename()));
            azquoBook.setWb(wb);
            String error = printBody(loggedInConnection, onlineReport.getId(), azquoBook, sb);
            if (error.length() > 0){
                return error;
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        head.append("<style>\n");
        head.append(azquoBook.printAllStyles());
        head.append(readFile("excelStyle.css"));
        head.append("</style>\n");
        output = output.replace("$script",readFile("online.js"));
        output = output.replace("$topcell", azquoBook.getTopCell()+"").replace("$leftcell",azquoBook.getLeftCell()+"").replace("$maxheight",azquoBook.getMaxHeight() + "px").replace("$maxwidth",azquoBook.getMaxWidth() + "px");
        output = output.replace("$maxrow",azquoBook.getMaxRow()+"").replace("$maxcol",azquoBook.getMaxCol()+"");
         output = output.replace("$reportid", onlineReport.getId() + "").replace("$connectionid", loggedInConnection.getConnectionId() +"");
         output = output.replace("$regions", azquoBook.getRegions(loggedInConnection));
         output = output.replace("$styles", head.toString()).replace("$workbook", sb.toString());

        return output;
    }



    public String printBody(LoggedInConnection loggedInConnection, int reportId, AzquoBook azquoBook, StringBuffer output) throws Exception {


        Formatter out = new Formatter(output);

        azquoBook.setSheet(0);
        String error = azquoBook.prepareSheet(loggedInConnection, reportId, valueService, userChoiceDAO);
        if (error.startsWith("error:")){
            return error;
        }



        output.append(azquoBook.printBody(loggedInConnection, nameService));
     /*
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

*/

        return "";
    }




    StringBuffer readFile(String filename){

         // First, copy the base css
        StringBuffer sb = new StringBuffer();
        BufferedReader in = null;
        try {
            in = new BufferedReader(new InputStreamReader(
                    getClass().getResourceAsStream(filename)));
            String line;
            while ((line = in.readLine()) != null) {
                sb.append(line + "\n");
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
        return sb;

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

    public void saveBook(HttpServletResponse response, LoggedInConnection loggedInConnection, String fileName)throws Exception{

        Workbook wb = loggedInConnection.getAzquoBook().getWb();

            response.setContentType("application/vnd.ms-excel"); // Set up mime type
            response.addHeader("Content-Disposition", "attachment; filename=" + fileName);
            OutputStream out = response.getOutputStream();
            wb.write(out);
            out.flush();

    }

    public String getChart(LoggedInConnection loggedInConnection,String region){

        AzquoBook azquoBook = loggedInConnection.getAzquoBook();
        region = azquoBook.confirmRegion(region).toLowerCase();

        com.azquo.util.Chart chart = new com.azquo.util.Chart();
        return chart.drawChart(azquoBook.makeChartTitle(region), azquoBook.getChartHeadings(region,"row"), azquoBook.getChartHeadings(region,"column"), azquoBook.getData(region));



    }

    public String changeValue(LoggedInConnection loggedInConnection, String region, int row, int col, String value){
               return loggedInConnection.getAzquoBook().changeValue(region, row, col, value);

    }

    public String getProvenance(LoggedInConnection loggedInConnection, int row, int col, String jsonFunction){
                return loggedInConnection.getAzquoBook().getProvenance(loggedInConnection, valueService, row, col, jsonFunction);
    }



}