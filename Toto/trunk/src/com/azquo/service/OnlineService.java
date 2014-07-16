package com.azquo.service;

import com.azquo.admindao.*;
import com.azquo.adminentities.*;
import com.azquo.memorydbdao.*;
import com.azquo.view.AzquoBook;
import org.apache.commons.fileupload.FileItem;
//import org.apache.poi.ss.usermodel.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    @Autowired
    OnlineReportDAO onlineReportDAO;

    @Autowired
    LoginRecordDAO loginRecordDAO;

    @Autowired
    UploadRecordDAO uploadRecordDAO;

    @Autowired
    PermissionDAO permissionDAO;

    @Autowired
    OpenDatabaseDAO openDatabaseDAO;

    @Autowired
    UserDAO userDAO;


    public String readExcel(LoggedInConnection loggedInConnection, OnlineReport onlineReport, String spreadsheetName, String message)throws Exception {


        if (onlineReport.getId()==1 && !loggedInConnection.getUser().isAdministrator()){
            onlineReport = onlineReportDAO.findById(-1);//user report list replaces admin sheet
        }

        String popup = "";
        if (message.startsWith("popup:")){
            popup = "<div id=\"namelistpopup\" class=\"namelistpopup\"> <div class=\"closebutton\"><a href=\"#\" onclick=\"hideNameList();\"><img src=\"/images/closebutton.png\" alt=\"close button\"/></a></div>\n" +
                    "<div class=\"content\"> " + nameService.convertJsonToHTML(message.substring(6)) + "</div>   <div id=\"namedetailssubmit\"><a href=\"#\" onclick=\"nameIdChosen()\">Submit</a></div></div>";
            message = "";
        }
        StringBuffer worksheet = new StringBuffer();
        StringBuffer tabs = new StringBuffer();
        StringBuffer head = new StringBuffer();
        AzquoBook azquoBook = new AzquoBook(valueService, adminService, nameService, userChoiceDAO);
        loggedInConnection.setAzquoBook(azquoBook);
        String output = readFile("onlineReport.html").toString();
        if (spreadsheetName == null){
            spreadsheetName = "";
        }
        if (onlineReport.getId()==1 && spreadsheetName.equals("Upload")) {
            output = output.replace("$enctype", " enctype=\"multipart/form-data\" ");
        }else{
            output = output.replace("$enctype","");
        }
        try {
            azquoBook.loadBook(onlineReport.getFilename());
            azquoBook.dataRegionPrefix = AzquoBook.azDataRegion;
            if (onlineReport.getId()==1 || onlineReport.getId()==-1){//this is the maintenance workbook
                azquoBook.dataRegionPrefix = azquoBook.azInput;

            }
            azquoBook.printTabs(tabs, spreadsheetName);

            String error = azquoBook.convertSpreadsheetToHTML(loggedInConnection, onlineReport.getId(), spreadsheetName, worksheet);
            if (error.length() > 0){
                message = error;
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        head.append("<style>\n");
        head.append(azquoBook.printAllStyles());
        head.append(readFile("excelStyle.css"));
        head.append("</style>\n");
        output = output.replace("$script",readFile("online.js"));
        output = output.replace("$topmenu",createTopMenu(loggedInConnection));
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
        if (azquoBook.dataRegionPrefix.equals(AzquoBook.azDataRegion)){

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


    public String executeSheet(LoggedInConnection loggedInConnection, OnlineReport onlineReport, String spreadsheetName) throws Exception{
        String error = "";
        AzquoBook azquoBook = new AzquoBook(valueService, adminService, nameService, userChoiceDAO);
        loggedInConnection.setAzquoBook(azquoBook);
        return azquoBook.executeSheet(loggedInConnection, onlineReport.getFilename(), spreadsheetName, onlineReport.getId());
    }





    private StringBuffer createTopMenu(LoggedInConnection loggedInConnection){
        StringBuffer sb = new StringBuffer();
        sb.append("<ul class=\"topmenu\">\n");
        sb.append("<li><a href=\"#\" onclick=\"downloadWorkbook();\">Download</a></li>\n");
        if (loggedInConnection.getAzquoBook().dataRegionPrefix.equals(AzquoBook.azDataRegion)) {
            sb.append("<li><input type=\"checkbox\" id=\"withMacros\" value=\"\">with macros</li>\n");
            sb.append(menuItem("Draw chart", "drawChart()", " id=\"drawChart\""));
        }
        sb.append(menuItem("Save data", "saveData()"," id=\"saveData\" style=\"display:none;\""));
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

        loggedInConnection.getAzquoBook().saveBook(response, fileName);


    }

    public void saveBookActive(HttpServletResponse response, LoggedInConnection loggedInConnection, String fileName)throws Exception{
         loggedInConnection.getAzquoBook().saveBookActive(response, fileName);
    }




    public String getChart(LoggedInConnection loggedInConnection,String region){

        AzquoBook azquoBook = loggedInConnection.getAzquoBook();
        region = azquoBook.confirmRegion(region).toLowerCase();

        com.azquo.util.Chart chart = new com.azquo.util.Chart();
        return chart.drawChart(azquoBook.makeChartTitle(region), azquoBook.getChartHeadings(region,"row"), azquoBook.getChartHeadings(region,"column"), azquoBook.getData(region));



    }

    public String changeValue(LoggedInConnection loggedInConnection, String region, int row, int col, String value){
                return loggedInConnection.getAzquoBook().changeValue(region, row, col, value, loggedInConnection);

    }

    public String getProvenance(LoggedInConnection loggedInConnection, int row, int col, String jsonFunction){
                return loggedInConnection.getAzquoBook().getProvenance(loggedInConnection, row, col, jsonFunction);
    }

    private String convertDatetoSQL(String dateSent){
        if (dateSent.charAt(2) == '/'){
            return "20" + dateSent.substring(6,7) + "-" + dateSent.substring(3,4) + "-" + dateSent.substring(0,2);
        }
        return dateSent;
    }

    private String convertNametoSQL(String fieldName){
        StringBuffer sb = new StringBuffer();
        for (int i = 0;i<fieldName.length();i++){
            char ch = fieldName.charAt(i);
            if (ch >='a'){
                sb.append(ch);
            }else{
                sb.append(("_" + ch).toLowerCase());
            }
        }
        return sb.toString();
    }


    public String saveAdminData(LoggedInConnection loggedInConnection, String jsonFunction){
        String result = "";
        AzquoBook azquoBook=loggedInConnection.getAzquoBook();
        String tableName = azquoBook.getAdminTableName();
        StringBuffer data = azquoBook.getAdminData();
        if (data == null){
            result = "error: no data to save";
        }else{
            StringTokenizer st = new StringTokenizer(data.toString(),"\n");
            String headingsList = st.nextToken();
            String[] headings = headingsList.split("\t");
             while (st.hasMoreTokens()){
                String dataLine = st.nextToken();
                StringBuffer sqlSet = new StringBuffer();
                sqlSet.append("update `" + tableName + "` set ");
                 final Map<String, Object> parameters = new HashMap<String, Object>();
                 StringTokenizer st2 = new StringTokenizer(dataLine,"\t");
                String idVal = st2.nextToken();
                int id = 0;
                 if (idVal.length() > 0){
                     try{
                         id=Integer.parseInt(idVal);
                     }catch(Exception e){

                     }
                 }
                for (int i = 1;i <headings.length;i++){
                    String value = "";
                    if (st2.hasMoreTokens()){
                        value = st2.nextToken();
                    }
                    String heading = convertNametoSQL(headings[i]);
                    if (heading.contains("date")){
                        parameters.put(heading, interpretDate(value));
                    }else {
                        parameters.put(convertNametoSQL(headings[i]), value);
                    }

                 }
                 if (tableName.equalsIgnoreCase("online_report")){
                     onlineReportDAO.update(id, parameters);
                 }else if (tableName.equals("permission")){
                     String dbName = (String) parameters.get("database");
                     if (dbName!=null && dbName.length() > 0){
                         Database db = databaseDAO.findForName(loggedInConnection.getBusinessId(), dbName);
                         if (db!=null){
                             parameters.put("database_id", db.getId());
                             String email = (String) parameters.get("email");
                             if (email != null && email.length() > 0){
                                 User user = userDAO.findByEmail(email);
                                 if (user!=null){
                                     parameters.put("user_id", user.getId());
                                     parameters.remove("email");
                                     parameters.remove("database");
                                     permissionDAO.update(id, parameters);

                                 }
                             }
                         }
                     }


                 }else if (tableName.equals("user")){
                     userDAO.update(id, loggedInConnection.getBusinessId(), parameters);
                 }

             }
        }
        return result;
    }

    public String saveData(LoggedInConnection loggedInConnection, String jsonFunction)throws Exception{

        AzquoBook azquoBook = loggedInConnection.getAzquoBook();
        String result = "";
        if (azquoBook.dataRegionPrefix.equals(AzquoBook.azInput)){
            result = saveAdminData(loggedInConnection, jsonFunction);
        }else {
            result = azquoBook.saveData(loggedInConnection);
        }
        if (result.length()==0){
            result = "data saved successfully";
        }
        return jsonFunction + "({\"message\":\"" + result + "\"})";
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
            if (foundDatabases.size()==1){
                for (String dbName:foundDatabases.keySet()){
                    sb.append(dbName);
                }
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
        String nameList = "";
        int nameId = 0;
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

                }else if(paramName.equals("filename")) {
                    filename = paramValue;
                }else if (paramName.equals("database")){
                    database = paramValue;
                }else if (paramName.equals("newdatabase")){
                    newdatabase = paramValue;
                }else if (paramName.equals("searchterm")){
                    searchTerm = paramValue;
                }else if (paramName.equals("namelist")){
                    nameList = paramValue;
                }else if (paramName.equals("nameid")){
                    try {
                        nameId = Integer.parseInt(paramValue);
                    }catch(Exception e){
                      //ignore = use whole list
                    }
                }
                paramName = "";
                if (link.indexOf("=")> 0){
                    paramName = link.substring(0,link.indexOf("="));
                    link = link.substring(paramName.length() + 1);
                }
            }


        }
        if (database.length()>0) {
            Database db = databaseDAO.findForName(loggedInConnection.getBusinessId(), database);
            if (db == null) {
                return database + " - no such database";
            }
            loginService.switchDatabase(loggedInConnection, db);
        }
        if (op.equalsIgnoreCase("newdatabase")){
            if (newdatabase.length() > 0) {
                message =  adminService.createDatabase(newdatabase, loggedInConnection) + "";
            }

        }
        if (op.equalsIgnoreCase("copydatabase")){
            message =  adminService.copyDatabase(loggedInConnection, database, nameList);
        }
        if (op.equals("delete")){
            loginService.switchDatabase(loggedInConnection, null);
            Database db = databaseDAO.findForName(loggedInConnection.getBusinessId(), database);

            if (db!=null) {
                List<OnlineReport> onlineReports = onlineReportDAO.findForDatabaseId(db.getId());
                for (OnlineReport onlineReport:onlineReports){
                    userChoiceDAO.deleteForReportId(onlineReport.getId());
                }
                loginRecordDAO.removeForDatabaseId(db.getId());
                onlineReportDAO.removeForDatabaseId(db.getId());
                openDatabaseDAO.removeForDatabaseId(db.getId());
                permissionDAO.removeForDatabaseId(db.getId());
                uploadRecordDAO.removeForDatabaseId(db.getId());
                String mySQLName = db.getMySQLName();



                databaseDAO.removeById(db);
                message = adminService.dropDatabase(mySQLName);
                if (message.length()==0){
                    message = "database deleted successfully";
                }


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
            message = nameService.getStructureForNameSearch(loggedInConnection,searchTerm, nameId);
            if (message.startsWith("error:")) return message;
            return "popup:" + message;

        }

        return message;
    }

    public static Date interpretDate(String dateString){
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
        SimpleDateFormat dateFormat2 = new SimpleDateFormat("dd/MM/yy");
        SimpleDateFormat dateFormat3 = new SimpleDateFormat("dd/MM/yyyy");
        SimpleDateFormat dateFormat4 = new SimpleDateFormat("dd-MM-yyyy");
        Date dateFound = null;
        if (dateString.length() > 5) {
            if (dateString.substring(2, 3).equals("/")) {
                if (dateString.length() > 8) {
                    try {
                        dateFound = dateFormat3.parse(dateString);
                    } catch (Exception ignored) {
                    }

                } else {
                    try {
                        dateFound = dateFormat2.parse(dateString);
                    } catch (Exception ignored) {
                    }
                }
            } else {
                if (dateString.substring(2, 3).equals("-")) {
                    try{
                        dateFound = dateFormat4.parse(dateString);
                    }catch (Exception ignored){
                    }
                }else{
                    try {
                        dateFound = simpleDateFormat.parse(dateString);
                    } catch (Exception ignored) {
                    }
                }
            }
        }
        return dateFound;

    }





}