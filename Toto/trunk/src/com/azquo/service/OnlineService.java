package com.azquo.service;

import com.azquo.admindao.DatabaseDAO;
import com.azquo.admindao.UserChoiceDAO;
import com.azquo.adminentities.Database;
import com.azquo.adminentities.OnlineReport;
import com.azquo.adminentities.UserChoice;
import org.apache.commons.fileupload.FileItem;
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

    @Autowired
    LoginService loginService;

    @Autowired
    AdminService adminService;

    @Autowired
    ImportService importService;

    @Autowired
    DatabaseDAO databaseDAO;


    public String readExcel(LoggedInConnection loggedInConnection, OnlineReport onlineReport, String spreadsheetName, String message) {


        String popup = "";
        if (message.startsWith("popup:")){
            popup = "<div id=\"namelistpopup\" class=\"namelistpopup\"> <div class=\"closebutton\"><a href=\"#\" onclick=\"hideNameList();\"><img src=\"https://data.azquo.com:8443/images/closebutton.png\" alt=\"close button\"/></a></div>\n" +
                    "<div class=\"content\"> " + nameService.convertJsonToHTML(message.substring(6)) + "</div></div>";
            message = "";
        }
        StringBuffer worksheet = new StringBuffer();
        StringBuffer tabs = new StringBuffer();
        StringBuffer head = new StringBuffer();
        AzquoBook azquoBook = new AzquoBook();
        loggedInConnection.setAzquoBook(azquoBook);
        String output = readFile("onlineReport.html").toString();
        try {
            Workbook wb  = WorkbookFactory.create(new FileInputStream(onlineReport.getFilename()));
            azquoBook.setWb(wb);
            azquoBook.dataRegionPrefix = "az_dataregion";
            if (onlineReport.getId()==1){//this is the maintenance workbook
                azquoBook.dataRegionPrefix = "az_input";

            }
            printTabs(azquoBook,tabs);

            String error = printBody(loggedInConnection, onlineReport.getId(), azquoBook, spreadsheetName, worksheet);
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
        output = output.replace("$topmenu",createTopMenu());
        output = output.replace("$tabs", tabs.toString());
        output=output.replace("$topmessage",message);
        if (spreadsheetName==null){
            spreadsheetName = "";
        }
        output = output.replace("$popup", popup);
        output=output.replace("$spreadsheetname",spreadsheetName);
        output = output.replace("$topcell", azquoBook.getTopCell()+"").replace("$leftcell",azquoBook.getLeftCell()+"").replace("$maxheight",azquoBook.getMaxHeight() + "px").replace("$maxwidth",azquoBook.getMaxWidth() + "px");
        output = output.replace("$maxrow",azquoBook.getMaxRow()+"").replace("$maxcol",azquoBook.getMaxCol()+"");
         output = output.replace("$reportid", onlineReport.getId() + "").replace("$connectionid", loggedInConnection.getConnectionId() +"");
         output = output.replace("$regions", azquoBook.getRegions(loggedInConnection, azquoBook.dataRegionPrefix));
         if (azquoBook.dataRegionPrefix.equals("az_dataregion")){
             output=  output.replace("$menuitems","[{\"position\":1,\"name\":\"Provenance\",\"enabled\":true,\"link\":\"showProvenance()\"},{\"position\":3,\"name\":\"Highlight changes\",\"enabled\":true,\"link\":\"showHighlight()\"}]");
         }else{
             output = output.replace("$menuitems","[{\"position\":1,\"name\":\"Provenance\",\"enabled\":true,\"link\":\"showProvenance()\"}," +
                     "{\"position\":2,\"name\":\"Edit\",\"enabled\":true,\"link\":\"edit()\"}," +
                     "{\"position\":3,\"name\":\"Cut\",\"enabled\":true,\"link\":\"cut()\"}," +
                     "{\"position\":4,\"name\":\"Copy\",\"enabled\":true,\"link\":\"copy()\"}," +
                     "{\"position\":5,\"name\":\"Paste before\",\"enabled\":true,\"link\":\"paste(0)\"}," +
                     "{\"position\":6,\"name\":\"Paste after\",\"enabled\":true,\"link\":\"paste(1)\"}," +
                     "{\"position\":7,\"name\":\"Paste into\",\"enabled\":true,\"link\":\"paste(2)\"}," +
                     "{\"position\":8,\"name\":\"Delete\",\"enabled\":true,\"link\":\"deleteName()\"}]");
         }
         output = output.replace("$styles", head.toString()).replace("$workbook", worksheet.toString());
         if (output.indexOf("$azquodatabaselist") > 0){
             output = output.replace("$azquodatabaselist", createDatabaseSelect(loggedInConnection));
         }
          if (output.indexOf("$fileselect") > 0){
              output = output.replace("$fileselect", "<input type=\"file\" name=\"uploadfile\">");
          }


        return output;
    }

    private String printTabs(AzquoBook azquoBook, StringBuffer tabs){
        String error = "";
        final int tabShift = 50;
        int left = 0;
        for (int sheetNo=0; sheetNo< azquoBook.getWb().getNumberOfSheets(); sheetNo++){
            Sheet sheet = azquoBook.getWb().getSheetAt(sheetNo);
            tabs.append("<div class=\"tab\" style=\"left:" + left + "px\"><a href=\"#\" onclick=\"loadsheet('" + sheet.getSheetName() + "')\">" + sheet.getSheetName() + "</a></div>\n");
            left+=tabShift;
        }
        return error;
    }



    private StringBuffer createTopMenu(){
        StringBuffer sb = new StringBuffer();
        sb.append("<ul class=\"topmenu\">\n");
        sb.append(menuItem("Download", "downloadSheet()",""));
        sb.append(menuItem("Draw chart", "drawChart()",""));
        sb.append(menuItem("Save data", "saveData()"," id=\"savedata\" style=\"display:none;\""));
        sb.append("</ul>");
        return sb;


    }

    private StringBuffer menuItem(String name, String link, String itemClass){
        return menuItem(name, link, itemClass, "");

    }


        private StringBuffer menuItem(String name, String link, String itemClass, String submenu){
        StringBuffer sb = new StringBuffer();
        sb.append("<li");
        if (itemClass.length() > 0) sb.append(itemClass);
        sb.append("><a href=\"#\" onclick=\"" + link + "\">" + name + "</a>" + submenu + "</li>\n");
        return sb;
    }


    public String printBody(LoggedInConnection loggedInConnection, int reportId, AzquoBook azquoBook, String spreadsheetName,StringBuffer output) throws Exception {


        azquoBook.setSheet(0);
        if (spreadsheetName != null) {
            for (int sheetNo = 0; sheetNo < azquoBook.getWb().getNumberOfSheets(); sheetNo++) {
                Sheet sheet = azquoBook.getWb().getSheetAt(sheetNo);
                if (sheet.getSheetName().toLowerCase().equals(spreadsheetName.toLowerCase())) {
                    azquoBook.setSheet(sheetNo);
                }
            }
        }
        String error = azquoBook.prepareSheet(loggedInConnection, reportId, adminService, valueService, userChoiceDAO);
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
                return loggedInConnection.getAzquoBook().changeValue(region, row, col, value, loggedInConnection, valueService);

    }

    public String getProvenance(LoggedInConnection loggedInConnection, int row, int col, String jsonFunction){
                return loggedInConnection.getAzquoBook().getProvenance(loggedInConnection, valueService, row, col, jsonFunction);
    }


    public String saveData(LoggedInConnection loggedInConnection, String jsonFunction)throws Exception{

        String result = loggedInConnection.getAzquoBook().saveData(loggedInConnection, valueService);
        if (result.length()==0){
            result = "data saved successfully";
        }
        return jsonFunction + "({\"message\":\"" + result + "\"})";
    }

    public String showAdminMenu(LoggedInConnection loggedInConnection){
        StringBuffer head = new StringBuffer();
         head.append("<style>\n");
        head.append(readFile("excelStyle.css"));
        head.append("</style>\n");


        String output = readFile("onlineReport.html").toString();
        output = output.replace("$script",readFile("online.js"));
        output = output.replace("$topmenu",createAdminMenu());
        output=output.replace("$topmessage",createDatabaseSelect(loggedInConnection));
        output = output.replace("$topcell", "").replace("$leftcell","").replace("$maxheight","1000px").replace("$maxwidth", "2000px");
        output = output.replace("$maxrow", "0").replace("$maxcol","0");
        output = output.replace("$reportid", "0").replace("$connectionid", loggedInConnection.getConnectionId() +"");
        output = output.replace("$regions", "");
        output = output.replace("$styles", head.toString()).replace("$workbook", "");


        return output.toString();
    }

    private StringBuffer createDatabaseSelect(LoggedInConnection loggedInConnection){
        StringBuffer sb = new StringBuffer();
        String chosen = "";
        Map<String, Database> foundDatabases = loginService.foundDatabases(loggedInConnection.getUser());
        if (foundDatabases.size() > 1){
            if (loggedInConnection.getAzquoMemoryDB()!=null) chosen = loggedInConnection.getCurrentDBName();
            sb.append("<select class=\"databaseselect\" name=\"database\" value=\"" + chosen + "\">\n");
            if (chosen.length() == 0) {
                sb.append("<option value=\"\">No database chosen</option>");
            }
            for (String dbName:foundDatabases.keySet()){
                sb.append("<option value =\"" + dbName + "\"");
                if (dbName.equals(chosen)) sb.append(" selected");
                sb.append(">" + dbName + "</option>\n");

            }
            sb.append("</select>");

        }else{
            if (chosen.length() > 0){
                sb.append(chosen);
            }else{
                sb.append("No database available");
            }
        }
        return sb;
    }

    private StringBuffer createAdminMenu(){
        //not currently used - the spreadsheet tabs on the maintenance workbook are used instead
        StringBuffer sb = new StringBuffer();
        sb.append("<ul class=\"topmenu\">\n");
        StringBuffer submenu = new StringBuffer();
        submenu.append("<ul>");
        submenu.append(menuItem("New", "manageDatabases('new')", ""));
        submenu.append(menuItem("Copy", "manageDatabases('copy')",""));
        submenu.append(menuItem("Merge", "manageDatabases('merge')", ""));
        submenu.append(menuItem("Restore", "manageDatabases('restore')",""));
        submenu.append("</ul>\n");
        sb.append(menuItem("Database", "manageDatabases()","", submenu.toString()));

        sb.append(menuItem("Upload", "upload()",""));
        sb.append(menuItem("Inspect", "inspect()",""));
        sb.append(menuItem("Users", "manageUsers()",""));
        sb.append(menuItem("Permissions", "managePermissions()",""));

        //sb.append(menuItem("Draw chart", "drawChart()",""));
        //sb.append(menuItem("Save data", "saveData()"," id=\"savedata\" style=\"display:none;\""));
        sb.append("</ul>");
        return sb;


    }

    public String followInstructionsAt(LoggedInConnection loggedInConnection, String jsonFunction, int rowNo, int colNo, String database, FileItem item)throws Exception{
        //this routine is called when a button on the maintenance spreadsheet is pressed

        AzquoBook azquoBook = loggedInConnection.getAzquoBook();
        String result = azquoBook.getCellContent(rowNo, colNo);
        String message = "button formula not understood";
        String op="";
        String newdatabase = "";
        String filename = "";
        String searchTerm = "";
        if (result.startsWith("$button;name=") && result.indexOf("op=") > 0){
            String link = result.substring(result.indexOf("op=")+ 3);
            String paramName = "op";
            while (paramName.length() > 0){
                String paramValue = link.substring(0,(link+"&").indexOf("&"));
                if (paramValue.length() < link.length()){
                    link = link.substring(paramValue.length() + 1);
                }else{
                    link="";
                }
                if (paramName.equals("op")){
                    op = paramValue;

                }else if(paramName.equals("filename")){
                    filename = paramValue;
                }else if (paramName.equals("newdatabase")){
                    newdatabase = paramValue;
                }else if (paramName.equals("searchterm")){
                    searchTerm = paramValue;
                }
                paramName = "";
                if (link.indexOf("=")> 0){
                    paramName = link.substring(0,link.indexOf("="));
                    link = link.substring(paramName.length() + 1);
                }
            }


        }
        if (database.length()>0) {
            Database db = databaseDAO.findForName(loggedInConnection.getUser().getBusinessId(), database);
            if (db == null) {
                return database + " - no such database";
            }
            loginService.switchDatabase(loggedInConnection, db);
        }
        if (op.equalsIgnoreCase("newdatabase")){
            if (newdatabase.length() > 0) {
                return adminService.createDatabase(newdatabase, loggedInConnection) + "";
            }

        }
        if (op.equalsIgnoreCase("upload")){
            InputStream uploadFile = item.getInputStream();
            String fileName = item.getName();
              message = importService.importTheFile(loggedInConnection, fileName, uploadFile, "", "", true);
            if (message.length()==0){
                message="file imported successfully";
            }
        }
        if (op.equalsIgnoreCase("inspect")){
            message = nameService.getStructureForNameSearch(loggedInConnection,searchTerm);
            if (message.startsWith("error:")) return message;
            return "popup:" + message;

        }

        return message;
    }


}