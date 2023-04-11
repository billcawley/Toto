package com.azquo.admin;

import com.azquo.RowColumn;
import com.azquo.StringLiterals;
import com.azquo.admin.controller.ManageDatabasesController;
import com.azquo.admin.database.*;
import com.azquo.admin.onlinereport.*;
import com.azquo.admin.user.UserDAO;
import com.azquo.dataimport.*;
import com.azquo.rmi.RMIClient;
import com.azquo.spreadsheet.CommonReportUtils;
import com.azquo.spreadsheet.LoggedInUser;
import com.azquo.spreadsheet.SpreadsheetService;
import com.azquo.spreadsheet.transport.HeadingWithInterimLookup;
import com.azquo.spreadsheet.transport.UploadedFile;
import com.azquo.spreadsheet.zk.BookUtils;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang.math.NumberUtils;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.ui.ModelMap;
import org.springframework.web.multipart.MultipartFile;
import org.zeroturnaround.zip.ZipUtil;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;


public class DisplayService {


    public static class Progress {
        public int rowNo;
        public int level;

        Progress(int rowNo, int level) {
            this.rowNo = rowNo;
            this.level = level;
        }
    }

    public static class ErrorCount {
        String errorName;
        int errorCount;

        ErrorCount(String errorName, int errorCount){
            this.errorName = errorName;
            this.errorCount = errorCount;
        }
    }




    public static class DisplaySpec {
        Object records;
        Object headings;
        List<String> buttons;
        List<String> dbNames;
        int recordsToShow;
        List<Map<String,Object>>stages;
        int stageNo;
        boolean showFilter;
        String allowNew;
        String recordClass;
        String hRef;

        public DisplaySpec(Object records, Object headings, List<String>buttons, List<Map<String,Object>>stages, int stageNo, List<String> dbNames, int recordsToShow, boolean showFilter, String allowNew, String recordClass, String hRef) {
            this.records = records;
            this.headings = headings;
            this.buttons = buttons;
            this.stages = stages;
            this.dbNames = dbNames;
            this.stageNo = stageNo;
            this.recordsToShow = recordsToShow;
            this.showFilter = showFilter;
            this.allowNew = allowNew;
            this.recordClass = recordClass;
            this.hRef = hRef;
          }
    }


    static DateTimeFormatter sdf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public static class RecentlyViewed {
        String name;
        String database;
        LocalDateTime time;
        int id;

        RecentlyViewed(String name, String database, LocalDateTime time, int id) {
            this.name = name;
            this.database = database;
            this.time = time;
            this.id = id;
        }

    }


    public static class EditFieldSpec{
        String displayName;
        String type;
        String nameOnFile;
        List<String> choices;

        EditFieldSpec(String displayName, String type, String nameOnFile, List<String>choices){
            this.displayName = displayName;
            this.type = type;
            this.nameOnFile = nameOnFile;
            this.choices = choices;
        }
    }


    public static class MenuItem {
        String name;
        String href;
        String icon;

        MenuItem(String name, String href, String icon) {
            this.name = name;
            this.href = href;
            this.icon = icon;
        }

        public String getName() {
            return name;
        }

        public String getHref() {
            return href;
        }

        public String getIcon() {
            return icon;
        }
    }

    private static List<RecentlyViewed> getRecentActivities(LoggedInUser loggedInUser) {
        List<UserActivity> reportsLoaded = UserActivityDAO.findMostRecentPeportsForUserAndBusinessId(loggedInUser.getUser().getBusinessId(), loggedInUser.getUser().getEmail(), 3);
        List<RecentlyViewed> recentlyViewed = new ArrayList<>();
        for (UserActivity reportLoaded : reportsLoaded) {
            String reportName = reportLoaded.getParameters().get("Report");
            recentlyViewed.add(new RecentlyViewed(reportName, DatabaseDAO.findById(reportLoaded.getDatabaseId()).getName(), reportLoaded.getTimeStamp(), OnlineReportDAO.findForNameAndBusinessId(reportName, loggedInUser.getBusiness().getId()).getId()));
        }
        return recentlyViewed;
    }


    private static List<OnlineReport> loadReports(LoggedInUser loggedInUser) {
        return AdminService.getReportList(loggedInUser, true);
    }

    private static List<UploadRecord.UploadRecordForDisplay> loadImports(LoggedInUser loggedInUser) {
        return AdminService.getUploadRecordsForDisplayForBusinessWithBasicSecurity(loggedInUser, "", false);//no auto loads
    }

    private static List<PendingUpload.PendingUploadForDisplay> loadPendingUploads(LoggedInUser loggedInUser) {
        return AdminService.getPendingUploadsForDisplayForBusinessWithBasicSecurity(loggedInUser, "", true, false);
    }


    public static String getJson(LoggedInUser loggedInUser, String page, String version, int id) {
        Map<String, Object> toSend = new HashMap<>();
        try {
            if (page != null && page.length() > 0) {
                if (page.endsWith("s")){
                    page = page.substring(0,page.length()-1);
                }
                Map<String,Object> tables =(Map<String,Object>)loggedInUser.getPermissions().get("Table");
                Map<String, Object> toReturn = new LinkedHashMap<>();
                toReturn.put("menus", loggedInUser.getPermissions().get("Menus"));
                toReturn.put("theme", loggedInUser.getPermissions().get("Theme"));
                toReturn.put("action","show");
                List<Object> recordsToShow = (List<Object>)loggedInUser.getPermissions().get("Records to show");
                List<String> selectedRecordsToShow = new ArrayList<>();
                Map<String,String>defaultShow = null;
                Map<String,Map<String,String>> showParameters = new HashMap<>();
                for (int i=0;i<recordsToShow.size();i++){
                    Map<String,String> recordToShow = (Map<String,String>)recordsToShow.get(i);
                    if (page.equalsIgnoreCase(recordToShow.get("Menu Item"))) {
                        selectedRecordsToShow.add(recordToShow.get("Table name"));
                        showParameters.put(recordToShow.get("Table name"),recordToShow);
                    }
                    if ("Default".equals(recordToShow.get("Menu Item"))){
                        defaultShow = recordToShow;
                    }
                }
                if (selectedRecordsToShow.size()==0){
                    selectedRecordsToShow.add(page);
                    showParameters.put(page,defaultShow);
                }
                toReturn.put("recordsToShow", selectedRecordsToShow);
                Map<String,Object>tableList = (Map<String,Object>)toReturn.get("Table");
                if (tableList ==null){
                    tableList = new HashMap<>();
                }
                List<String> buttons = new ArrayList<>();
                List<Map<String,Object>> stages = new ArrayList<>();
                int stageNo = 0;
                for (String tableName:selectedRecordsToShow) {
                    Map<String,Object> headings = new LinkedHashMap<>();
                    try{
                        //THIS TIRESOME ITERATION IS SIMPLY TO CONVER THE 'Number' attribute to a number.
                        Map<String,Object> table = (Map<String,Object>)tables.get(tableName);
                        if (table.get("Stages") !=null && stageNo == 0){
                            List<Map<String,String>> stringStages = (List<Map<String,String>>)table.get("Stages");
                            for (Map<String,String> stringStage:stringStages){
                                Map<String,Object> stage = new HashMap<>();
                                for (String field:stringStage.keySet()){
                                    if (field.equals("Number")){
                                        stage.put(field,Integer.parseInt(stringStage.get(field)));
                                    }else{
                                        stage.put(field,stringStage.get(field));
                                    }
                                }
                                stages.add(stage);
                            }


                            stageNo=1;
                        }
                        if (stageNo > 0){
                            table = (Map <String,Object>)table.get("Stage " + stageNo);
                        }

                        List<Map<String,Object>>sheetHeadings =(List<Map<String,Object>>)table.get(version + " Headings");
                        for (Map<String,Object> sheetHeading:sheetHeadings){
                            String type = (String)sheetHeading.get("fieldtype");
                            if (type.equalsIgnoreCase("combo")) {
                                //find existing values as list
                                List<String> options = StandardDAO.findDistinctList(page.toLowerCase(), (String)sheetHeading.get("nameOnFile"));
                                sheetHeading.put("choices", options);
                            }
                            headings.put((String)sheetHeading.get("nameOnFile"), sheetHeading);


                        }
                    }catch(Exception e){
                        //??
                    }

                    List<Database> databases = DatabaseDAO.findForBusinessId(loggedInUser.getBusiness().getId());
                    List<String>dbNames = new ArrayList<>();
                    for (Database database:databases){
                        dbNames.add(database.getName());
                    }
                    Map<String,String> pageParas = showParameters.get(tableName);
                    int recsPerPage = Integer.parseInt(pageParas.get("Count/Page"));
                    boolean filter = pageParas.get("Filter").toUpperCase(Locale.ROOT).equals("TRUE");
                    String displayType = pageParas.get("Display Type");
                    String allowNew = pageParas.get("Allow New").toLowerCase();
                    boolean recordsOnly = pageParas.get("Records Only").toUpperCase(Locale.ROOT).equals("TRUE");

                    Object records = null;
                    if ("Recently Viewed".equals(tableName)) {
                        records =getRecentActivities(loggedInUser);

                    } else if ("Report".equals(tableName)) {
                        records = loadReports(loggedInUser);
                    } else if ("Import".equals(tableName)) {
                        records = loadImports(loggedInUser);
                    } else if ("User".equals(tableName)) {
                        records =(AdminService.getUserListForBusinessWithBasicSecurity(loggedInUser));
                    } else if ("Import Schedule".equals(tableName)) {
                        records=ImportScheduleDAO.findForBusinessId(loggedInUser.getBusiness().getId());
                    } else if ("Report Schedule".equals(tableName)) {
                        List<ReportSchedule> reportSchedules = new ArrayList<>();
                        for (Database db : databases) {
                            reportSchedules.addAll(ReportScheduleDAO.findForDatabaseId(db.getId()));
                        }
                        records =reportSchedules;
                    } else if ("Pending Upload".equals(tableName)) {
                        if (stageNo == 1) {
                            records =loadPendingUploads(loggedInUser);
                        }
                    }
                    if (version.equals("Edit")){
                        Map<String,Object> selected = new HashMap<>();

                        if (id > 0) {
                            for (Object record : (List<Object>) records) {
                                Map<String, Object> fieldList = (Map<String, Object>) record;
                                Object fieldId = fieldList.get("id");
                                if ((int) fieldId == id) {
                                    selected = fieldList;
                                    break;
                                }
                            }
                        }
                        List<Map<String,EditFieldSpec>> newRecords = new ArrayList<>();
                        for (String headingName:headings.keySet()){
                            Map<String,Object> heading =(Map<String,Object>)headings.get(headingName);
                            Map<String,EditFieldSpec> row = new HashMap<>();
                            row.put("Name",new EditFieldSpec((String)heading.get("name"),"show","",null));
                            String fieldValue = (String)selected.get(heading.get("name"));
                            if (fieldValue==null){
                                fieldValue = "";
                            }

                            row.put("Value",new EditFieldSpec(fieldValue, (String)heading.get("type"),(String)heading.get("nameOnFile"), (List<String>)heading.get("choices")));
                            newRecords.add(row);


                        }
                        Map<String,Object> newHeadings = new LinkedHashMap();
                        buttons.add("Save");
                        buttons.add("Reject");
                        Map<String,String> newHeading = new HashMap<>();
                        newHeading.put("nameOnFile","Name");
                        newHeadings.put("Name", newHeading);
                        Map<String,String> newHeading2 = new HashMap<>();
                        newHeading2.put("nameOnFile","Value");
                        newHeadings.put("Value",newHeading2);
                        tableList.put(tableName, new DisplaySpec(newRecords, newHeadings, buttons, stages, stageNo, dbNames,  newRecords.size(), filter, "false", "az-table", ""));

                    }else{
                        tableList.put(tableName, new DisplaySpec(records, headings, buttons, stages, stageNo, dbNames,  recsPerPage, filter, allowNew, "az-"+displayType, ""));
                    }

                }
                toReturn.put("table", tableList);
                return reactVersion(toReturn,"show");
            }




        } catch (Exception e) {
            e.printStackTrace();
        }
        return jsonError("error in loading " + page);
    }
    public static final Map<String, LoggedInUser> reactConnections = new ConcurrentHashMap<>();// simple, for the moment should do it
    // ok we need support for a user being in more than one business
    public static final Map<String, List<LoggedInUser>> reactMultiUserConnections = new ConcurrentHashMap<>();

    public static void recordConnection(Map<String,LoggedInUser>connections, LoggedInUser loggedInUser, String currentSession){
        boolean newUser = true;
        for (String existingSessionId : connections.keySet()) {
            LoggedInUser existingUser = connections.get(existingSessionId);
            if (existingUser.getUser().getId() == loggedInUser.getUser().getId()) {
                connections.put(currentSession, existingUser);
                if (!existingSessionId.equals(currentSession)) {
                    connections.remove(existingSessionId);
                }
                loggedInUser = existingUser;
                newUser = false;
            }
        }
        if (newUser) {
            connections.put(currentSession + "", loggedInUser);
        }

    }


    public static List<MenuItem> getSecondaryMenu() {
        return new ArrayList<>();
    }








    private static int firstCol(List<String> row) {
        if (row == null) {
            return -1;
        }
        for (int i = 0; i < row.size(); i++) {
            if (row.get(i).length() > 0) {
                return i;
            }
        }
        return -1;
    }

    public static Map<String,Object> sheetInfoToObject(ImportTemplateData importTemplateData) {


        List<List<String>> sheetData = new ArrayList<>();

        for (String sheet : importTemplateData.getSheets().keySet()) {
            for (List<String> row : importTemplateData.getSheets().get(sheet)) {
                List<String> cleanRow = new ArrayList<>();
                boolean hasData = false;
                for (String cell : row) {
                    if (cell.length() == 0 && hasData) {
                        break;
                    }
                    cleanRow.add(cell);
                    if (cell.length() > 0) {
                        hasData = true;
                    }
                }
                if (hasData) {
                    sheetData.add(cleanRow);
                }
            }
        }
        Progress progress = new Progress(0, 0);
        return makeObject(sheetData, progress);
    }

    private static Map<String,Object> makeObject( List<List<String>> sheetData, Progress progress) {

        Map<String,Object> toReturn = new HashMap<>();

        String title = null;
        int baseLevel = progress.level;
        List<String> list = new ArrayList<>();
        while (progress.rowNo < sheetData.size()) {
            List<String> row = sheetData.get(progress.rowNo);
            int newLevel = firstCol(row);

            if (newLevel < progress.level) {
                break;
            }
            if (newLevel == progress.level && list.size() > 0){
                toReturn.put(title,list);
                list = new ArrayList<>();
            }
            List<String> nextRow = new ArrayList<>();
            if (progress.rowNo < sheetData.size() - 1) {
                nextRow = sheetData.get(progress.rowNo + 1);
            }
            int nextLevel = firstCol(nextRow);
            if (newLevel == progress.level) {
                title = row.get(newLevel);
                progress.rowNo++;


            } else {
                List<Map<String,Object>> data = new ArrayList<>();
                if (firstCol(row) > progress.level && firstCol(row) == firstCol(nextRow) && row.size() - newLevel == 2) {
                   int inset = firstCol(row);
                    Map<String,Object> map = new HashMap<>();
                    while (progress.rowNo < sheetData.size() && firstCol(sheetData.get(progress.rowNo)) == inset && sheetData.get(progress.rowNo).size() == inset + 2){
                         row = sheetData.get(progress.rowNo);
                         map.put(row.get(inset), row.get(inset + 1));
                         progress.rowNo++;
                    }
                    toReturn.put(title,map);

                }else if (firstCol(row) > progress.level && firstCol(row) == firstCol(nextRow) && row.size() - newLevel > 2) {//NB TABLES MUST HAVE MORE THAN TWO COLUMNS - 1 = LIST  - 2 = MAPPINGT
                    List<String> headings = row;
                    while (progress.rowNo < sheetData.size() - 1 && firstCol(sheetData.get(progress.rowNo + 1)) == firstCol(headings) && sheetData.get(progress.rowNo + 1).size() <= headings.size()) {
                        Map<String,Object> line = new HashMap<>();
                        progress.rowNo++;
                        row = sheetData.get(progress.rowNo);

                        for (int i = newLevel; i < row.size(); i++) {
                            String value = row.get(i);
                            if (value.startsWith("menu:")){
                                List<String>choices = Arrays.asList(value.substring(5).split(","));
                                line.put("choices", choices);
                                value = "menu";
                            }
                            line.put(headings.get(i),value);
                        }
                        data.add(line);
                    }
                    toReturn.put(title,data);
                    list = new ArrayList<>();
                    progress.rowNo++;
                } else if (newLevel > progress.level) {
                    if (nextLevel > newLevel){
                        progress.level = newLevel;
                        toReturn.put(title, makeObject(sheetData, progress));
                        list = new ArrayList<>();
                        progress.level = baseLevel;
                    }else{
                        list.add(row.get(newLevel));
                        progress.rowNo++;
                    }

                }
            }
        }
        if (list.size()>0){
            toReturn.put(title,list);
        }
        return toReturn;
    }





    public static String compileUploadedFileResults(List<UploadedFile> uploadedFiles, int checkboxId, Set<String> comments) {

        List<Object> uploadMap = new ArrayList<>();
        Map<String,Object> headings = new LinkedHashMap<>();
        int id = 0;;
        for (UploadedFile uploadedFile : uploadedFiles) {
            Map<String,Object> fieldList = new HashMap<>();
            fieldList.put("id","" + (++id));
            StringBuilder sb = new StringBuilder();
            List<String> names = new ArrayList<>(uploadedFile.getFileNames()); // copy as I might change it
            boolean first = true;
            for (String name : names) {
                if (!first) {
                    sb.append(", ");
                }
                sb.append(name);
                first = false;
            }
            if (uploadedFile.isConvertedFromWorksheet()) {
                sb.append(" - Converted from worksheet");
            }
            addHeading(headings,"File name","list","list");
            fieldList.put("File name",sb.toString());
            List<ErrorCount> errorList = new ArrayList<>();
            if (!uploadedFile.getErrorHeadings().isEmpty()) {
                for (String errorHeading : uploadedFile.getErrorHeadings()) {
                    int linesWithThatError = 0;
                    for (UploadedFile.WarningLine warningLine : uploadedFile.getWarningLines()) {
                        if (warningLine.getErrors().keySet().contains(errorHeading)) {
                            linesWithThatError++;
                        }
                    }
                    errorList.add(new ErrorCount(errorHeading, linesWithThatError));
                }
                List<String> shownErrorList = new ArrayList<>();
                for (ErrorCount eCount:errorList){
                    shownErrorList.add(eCount.errorName + ":" + eCount.errorCount);
                }
                addHeading(headings,"Error list", "list","list");
                fieldList.put("Error list",shownErrorList);
            }
            List<String>showUploadParas = new ArrayList<>();
            for (String uPara:uploadedFile.getParameters().keySet()){
                showUploadParas.add(uPara + ":" + uploadedFile.getParameters().get(uPara));
            }
            addHeading(headings,"Upload Parameters", "list","list");
            fieldList.put("Upload Parameters",showUploadParas);
            List<String> timeMap = new ArrayList<>();
            timeMap.add("Time to process: " + uploadedFile.getProcessingDuration() + " ms");
            timeMap.add("Number of lines imported:" + uploadedFile.getNoLinesImported());
            timeMap.add("Number of values adjusted:"+ uploadedFile.getNoValuesAdjusted());
            timeMap.add("Number of names adjusted:"+uploadedFile.getNoNamesAdjusted());
            addHeading(headings,"Actions", "list","list");
            fieldList.put("Actions", timeMap);
            Collection<List<String>> fileHeadings= new ArrayList<>();
            List<String>shownFileHeadings = new ArrayList<>();
            if (uploadedFile.getHeadingsByFileHeadingsWithInterimLookup() != null) {
                List<String> headingsWithInterimLookupMap = new ArrayList<>();

                if (uploadedFile.getFileHeadings() != null) {
                    fileHeadings = uploadedFile.getFileHeadings();
                } else {
                    fileHeadings = uploadedFile.getHeadingsByFileHeadingsWithInterimLookup().keySet();
                }


                for (List<String> fileHeading : fileHeadings) {
                    String heading = "";
                    for (String subHeading : fileHeading) {
                        heading += subHeading.replaceAll("\n", " ") + " ";
                    }
                    shownFileHeadings.add(heading);
                    String path = "";
                    HeadingWithInterimLookup headingWithInterimLookup = uploadedFile.getHeadingsByFileHeadingsWithInterimLookup().get(fileHeading);
                    if (headingWithInterimLookup != null) {
                        if (headingWithInterimLookup.getInterimLookup() != null) {
                            path += "->" + headingWithInterimLookup.getInterimLookup().replaceAll("\n", " ");
                        }
                        path += "->" + headingWithInterimLookup.getHeading().replaceAll("\n", " ");
                    } else {
                        path = " ** UNUSED **";
                    }
                    headingsWithInterimLookupMap.add(heading+":"+path);
                }
                addHeading(headings,"Headings with interim lookup", "popup","popup");
                fieldList.put("Headings with interim lookup",headingsWithInterimLookupMap);
            }

            if (uploadedFile.getHeadingsNoFileHeadingsWithInterimLookup() != null) {
                List<String> headingsNoFileHeadingsWithInterimLookup = new ArrayList<>();
                for (HeadingWithInterimLookup headingWithInterimLookup : uploadedFile.getHeadingsNoFileHeadingsWithInterimLookup()) {
                    String path = "";
                    if (headingWithInterimLookup.getInterimLookup() != null) { // it could be null now as we support a non file heading azquo heading on the Import Model sheet
                        path += headingWithInterimLookup.getInterimLookup() + " -> ";
                    }
                    path += headingWithInterimLookup.getHeading();
                    headingsNoFileHeadingsWithInterimLookup.add(path);
                }
                addPopupHeading(headings,"Headings no file headings with interim lookup", "list");
                fieldList.put("Headings no file headings with interim lookup", headingsNoFileHeadingsWithInterimLookup);
            }

            if (uploadedFile.getSimpleHeadings() != null) {
                addPopupHeading(headings,"Simple headings", "list");
                fieldList.put("Simple headings",uploadedFile.getSimpleHeadings());
            }

            if (!uploadedFile.getLinesRejected().isEmpty()) {
                List<String> lineErrors = new ArrayList<>();

                String error = "Line errors: " + uploadedFile.getLinesRejected().size() + "\n<br/>";
                error += "</b>";
                if (uploadedFile.getLinesRejected().size() < 100) {
                    for (UploadedFile.RejectedLine lineRejected : uploadedFile.getLinesRejected()) {
                        error += lineRejected.getLineNo() + ":" + lineRejected.getErrors() + "\n<br/>";
                    }
                }
                error += "<b>";
                lineErrors.add(makeFileName(uploadedFile) + " " + error);
                addHeading(headings,"Error line headings","list", "list");
                fieldList.put("Error line headings",uploadedFile.getFileHeadings());
                addHeading(headings,"Lines rejected","list", "list");
                fieldList.put("Lines rejected", uploadedFile.getLinesRejected());
            }
            if (uploadedFile.getPostProcessingResult() != null) {
                addHeading(headings,"Post processing result", "list","list");
                fieldList.put("Post processing result",uploadedFile.getPostProcessingResult());

            }
            Map<String, Object> warningLineMap = new HashMap<>();
            List<Object> tableHeadings = new ArrayList<>();
            tableHeadings.add(makeHeading("Load","CLICKBOX"));
            for (ErrorCount error:errorList){
                tableHeadings.add(makeHeading( error.errorName ,"show"));

            }
            for (String fieldName:shownFileHeadings){
                tableHeadings.add(makeHeading(fieldName,"show"));

            }
            Map<String,Object> warningLines = new HashMap<>();
            warningLines.put("headings", tableHeadings);
            List<Map<String,String>> wData = new ArrayList<>();
            for (UploadedFile.WarningLine warningLine:uploadedFile.getWarningLines()){
                Map<String,String> warningData = new HashMap<>();
                for (String error:warningLine.getErrors().keySet()){
                    warningData.put(error, error);
                }

                String[] fieldVals = warningLine.getLine().split("\t");
                int i = 0;
                for (String fieldName:shownFileHeadings){
                    if (i==fieldVals.length){
                        break;
                    }
                    if (fieldVals[i].length() > 0){
                        warningData.put(fieldName,fieldVals[i]);
                    }
                    i++;
                }
                warningData.put("id",warningLine.getLineNo()+"");
                wData.add(warningData);




            }

            warningLines.put("records", wData);

            addPopupHeading(headings,"Warning lines", "table");
            fieldList.put("Warning lines", warningLines);
            addHeading(headings,"Outcome", "list","list");
            List<String>errors = new ArrayList<>();
            if (!uploadedFile.isDataModified()) {
                if (uploadedFile.isNoData()) {
                    errors.add("NO DATA");
                } else {
                    errors.add("NO DATA MODIFIED");
                }
            }
            errors.add(uploadedFile.getError());
            fieldList.put("Outcome", errors);


            if (uploadedFile.getReportName() != null) {
                addHeading(headings,"Outcome", "list","list");
                fieldList.put("Outcome", "Report uploaded : " + uploadedFile.getReportName());
//                sb.append("Analysis : ").append(uploadedFile.getE()).append("\n<br/>");
            }

            if (uploadedFile.isImportTemplate()) {
                addHeading(headings,"Outcome", "list","list");
                fieldList.put("Outcome","Import template uploaded");
            }

            uploadMap.add( fieldList);
        }

        Map<String, Object> toReturn = new HashMap<>();
        Map<String,Object> article = new HashMap<>();
        article.put("name", "Pending Upload");
        article.put("stage",2);
        article.put("instructions","You can click on 'details' to see individual errors, When done, you can click below to load to the live database");
        article.put("headings", headings);
        article.put("records", uploadMap);
        toReturn.put("table", article);
        return reactVersion(toReturn,"show");

    }


    public static void addHeading(Map<String,Object>headings, String name, String fieldtype, String showtype){
        if (headings.get(name)!=null){
            return;
        }
        List<String> choices = new ArrayList<>();
        if (fieldtype.startsWith("menu:")){
            choices = Arrays.asList(showtype.split(","));
            showtype = "menu";
        }
        Map<String,String> heading = new HashMap<>();
        heading.put("DisplayHeading", name);
        heading.put("fieldtype", fieldtype);
        heading.put("showtype", showtype);
        heading.put("nameOnFile", name);
        headings.put("Choices", choices);
        headings.put(name,heading);
    }


    public static void addPopupHeading(Map<String,Object>headings, String name, String popupType){
        if (headings.get(name)!=null){
            return;
        }
        List<String> choices = new ArrayList<>();
        Map<String,String> heading = new HashMap<>();
        heading.put("DisplayHeading", name);
        heading.put("type", "popup");
        heading.put("popuptype", popupType);
        heading.put("nameOnFile", name);
        headings.put("Choices", choices);
        headings.put(name,heading);
    }



    public static String makeFileName(UploadedFile uploadedFile) {
        StringBuilder appendString = new StringBuilder();
        for (String fileName : uploadedFile.getFileNames()) {
            appendString.append(fileName + ".");
        }
        return appendString.toString();

    }

    private static Map<String,String>makeHeading(String headingName, String type){
        Map<String,String> heading = new HashMap<>();
        heading.put("nameOnFile", headingName);
        heading.put("DisplayHeading", headingName);
        heading.put("type", type);
        return heading;
    }

    public static String reactVersion(Map<String,Object> items, String action){
        items.put("action",action);
        Map<String,Object> data = new HashMap<>();
        data.put("data", items);
        final ObjectMapper jacksonMapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        jacksonMapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
        try {
            String string= jacksonMapper.writeValueAsString(data);
            return string;
        } catch (Exception e) {
            return jsonError(e.getMessage());
        }

    }


    public static String processPending(String op, String id, String table, LoggedInUser loggedInUser, MultipartFile amendmentsFile) throws Exception {


        final PendingUpload pu = PendingUploadDAO.findById(Integer.parseInt(id));
        // todo - pending upload security for non admin users?
        if (pu.getBusinessId() != loggedInUser.getUser().getBusinessId()) {
            return jsonError("User does not have access to this database");
        }
        Set<Integer> nonAdminDBids = null;
        // on general principle
        if ("reject".equals(op)) {
            Path loaded = Paths.get(SpreadsheetService.getFilesToImportDir() + "/loaded");
            if (!Files.exists(loaded)) {
                Files.createDirectories(loaded);
            }
            long timestamp = System.currentTimeMillis();
            String newFileName = pu.getFileName().substring(0, pu.getFileName().lastIndexOf(".")) + "rejected" + pu.getFileName().substring(pu.getFileName().lastIndexOf("."));
            Path rejectedDestination = loaded.resolve(timestamp + newFileName);
            Files.copy(Paths.get(pu.getFilePath()), rejectedDestination, StandardCopyOption.REPLACE_EXISTING);
            // then adjust the pending upload record to have the report
            pu.setImportResultPath(rejectedDestination.toString());
            pu.setFileName(newFileName);
            pu.setProcessedByUserId(loggedInUser.getUser().getId());
            pu.setProcessedDate(LocalDateTime.now());
            PendingUploadDAO.store(pu);
            return jsonAction("done");
        }
        if ("savecomment".equals(op) && id != null) {
            dealWithComment(pu, id, op);
            return jsonAction("done"); // stop it there, work is done
        }


        String mainFileName = pu.getFileName();
        String month;
        String importVersion;
        String[] fileSplit = mainFileName.replace("  ", " ").split(" "); // make double spaces be like single spaces for the purposes of parsing basic info
        String password = null; // password for excel files
        ImportTemplate preProcess = null;
        if (fileSplit.length < 3) {
            return jsonError("Filename in unknown format.");
        } else {
            //need to think about option to find a pre processor with the first and second words, if so the second word is the version
            String possiblePreprocessorName = fileSplit[0] + " " + fileSplit[1] + " preprocessor.xlsx";
            preProcess = ImportTemplateDAO.findForNameAndBusinessId(possiblePreprocessorName, loggedInUser.getUser().getBusinessId());
            if (preProcess != null) {
                importVersion = fileSplit[1];
            } else {
                importVersion = fileSplit[0] + fileSplit[1];
            }
            month = fileSplit[2];
            if (month.contains(".")) {
                month = month.substring(0, month.indexOf("."));
            }
        }
        // we say password can be at the end
        if (fileSplit.length > 3) {
            password = fileSplit[fileSplit.length - 1];
            if (password.contains(".")) {
                password = password.substring(0, password.indexOf("."));
            }
        }

        // before starting an import thread check we have database and parameters
        Database database = DatabaseDAO.findById(pu.getDatabaseId());
        // we have the database so set it to the user and look for the template
        Database db = DatabaseDAO.findById(pu.getDatabaseId());
        DatabaseServer databaseServer = DatabaseServerDAO.findById(db.getDatabaseServerId());
        loggedInUser.setDatabaseWithServer(databaseServer, db);
        // we've got the database but how about the import template?
        // we're going to need to move to an import template object I think
        ImportTemplateData importTemplateForUploadedFile = ImportService.getImportTemplateForUploadedFile(loggedInUser, null, null);
        List<List<String>> lookupSheet = null;
        String runClearExecuteCommand = null;
        if (importTemplateForUploadedFile == null) {
            return jsonError("Import template not found for " + database.getName() + ", please upload one for it.");
        } else {
            boolean found = false;
            for (String sheetName : importTemplateForUploadedFile.getSheets().keySet()) {
                if (sheetName.equalsIgnoreCase(importVersion)) {
                    found = true;
                    // a paste from import service stripped down to find just the parameters
                    Map<String, String> templateParameters = new HashMap<>(); // things like pre processor, file encoding etc
                    List<List<String>> standardHeadings = new ArrayList<>();// required to stop NPE internally, perhaps can zap . . .
                    ImportService.importSheetScan(importTemplateForUploadedFile.getSheets().get(sheetName), null, standardHeadings, null, templateParameters, null);
                    if (templateParameters.get(ImportService.PENDINGDATACLEAR) != null) {
                        runClearExecuteCommand = templateParameters.get(ImportService.PENDINGDATACLEAR);
                    }

                }
            }
            if (!found) {
                return jsonError("Import version " + importVersion + " not found.");
            }
        }




        /* STILL TO BE CONSIDERED
        List<UploadedFile> importResult = (List<UploadedFile>) session.getAttribute(ManageDatabasesController.IMPORTRESULT);
        compileUploadedFileResults(importResult, 0, true, null);

        // so we can go on this pending upload
        // new thread and then defer to import running as we do stuff
        StringBuilder lookupValuesForFilesHTML = new StringBuilder();
        lookupValuesForFilesHTML.append("<input name=\"paramssubmit\" value=\"paramssubmit\" type=\"hidden\" />");
        int counter = 0;
        // if no lookups  files will be empty, a moot point
        // to push the lookup values through the validation screen
        for (String file : files) {
            Map<String, String> mapForFile = lookupValuesForFiles.get(file);
            int counter2 = 0;
            for (String heading : lookupHeadings) {
                lookupValuesForFilesHTML.append("<input type=\"hidden\" name=\"" + counter + "-" + counter2 + "\" value=\"" + (mapForFile.get(heading) != null ? mapForFile.get(heading) : "") + "\"/>\n");
                counter2++;
            }
            counter++;
        }
        if (runClearExecute) {
            lookupValuesForFilesHTML.append("<input name=\"runClearExecute\" id=\"runClearExecute\" value=\"true\" type=\"hidden\" />\n");
        }


        final Map<String, Map<String, String>> finalLookupValuesForFiles = lookupValuesForFiles;
        if (!actuallyImport) { // before running validation grab the latest provenance - if it doesn't match when the actual import happens then warn the user about concurrent modification
            session.setAttribute(LATESTPROVENANCE, RMIClient.getServerInterface(loggedInUser.getDatabaseServer().getIp()).getMostRecentProvenance(loggedInUser.getDatabase().getPersistenceName()));
        } else {
            session.removeAttribute(LATESTPROVENANCE);
        }
        boolean finalActuallyImport = actuallyImport;
        String finalRunClearExecuteCommand = runClearExecute ? runClearExecuteCommand : null;// pass it through if they checked it . . .
        new Thread(() -> {
            if (finalLookupValuesForFiles != null && finalLookupValuesForFiles.get(pu.getFileName()) != null) { // could happen on a single xlsx upload. Apparently always zips but I'm concerned it may not be . . .
                params.putAll(finalLookupValuesForFiles.get(pu.getFileName()));
            }
            UploadedFile uploadedFile = new UploadedFile(pu.getFilePath(), Collections.singletonList(pu.getFileName()), params, false, !finalActuallyImport);
            try {
                PendingUploadConfig puc = new PendingUploadConfig(finalLookupValuesForFiles, fileRejectFlags, fileRejectLines, finalRunClearExecuteCommand);
                List<UploadedFile> uploadedFiles = ImportService.importTheFile(loggedInUser, uploadedFile, session, puc);
                if (!finalActuallyImport) {
                    session.setAttribute(PARAMSPASSTHROUGH, lookupValuesForFilesHTML.toString());
                } else {
                    Workbook wb = new XSSFWorkbook();
                    Sheet summarySheet = wb.createSheet("Summary");
                    summaryUploadFeedbackForSheet(uploadedFiles, summarySheet, pu);
                    Sheet sheet = wb.createSheet("Details");
                    formatUploadedFilesForSheet(pu, uploadedFiles, sheet);
                    // so we have the details sheet sheet but now we need to zip it up along with any files that were rejected.
                    // notably individual sheets in an xlsx file could be rejected so it's about collecting files from the original zip where any sheet might have been rejected
                    // also nested zips might not work at the mo
                    Set<String> filesRejected = new HashSet<>(); // gather level two files - those that will be zip entries. Set due to the point above - if there are multiple sheets in a file
                    for (UploadedFile rejectCheck : uploadedFiles) {
                        if (rejectCheck.getFileNames().size() > 1 && StringLiterals.REJECTEDBYUSER.equals(rejectCheck.getError())) {
                            filesRejected.add(rejectCheck.getFileNames().get(1));
                        }
                    }
//                        System.out.println(filesRejected);
                    // now, we need to make a new zip. A directory in the temp directory is probably the thing
                    Path zipforpendinguploadresult = Files.createTempDirectory("zipforpendinguploadresult");
                    // Write the output to a file
                    try (OutputStream fileOut = Files.newOutputStream(zipforpendinguploadresult.resolve("uploadreport.xlsx"))) {
                        wb.write(fileOut);
                    }
                    if (uploadedFile.getFileName().endsWith(".zip") || uploadedFile.getFileName().endsWith(".7z")) { // it will have been exploded already - just need to move relevant files in it
                        Files.list(Paths.get(uploadedFile.getPath())).forEach(path ->
                        {
                            if (filesRejected.contains(path.getFileName().toString())) {
                                try {
                                    Files.copy(path, zipforpendinguploadresult.resolve(path.getFileName()));
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }
                        });
                    } else { // just copy the file into there if it wasn't a zip
                        Files.copy(Paths.get(uploadedFile.getPath()), zipforpendinguploadresult.resolve(uploadedFile.getFileName()));
                    }
                    // pack up the directory
                    ZipUtil.unexplode(zipforpendinguploadresult.toFile());
                    Path loaded = Paths.get(SpreadsheetService.getFilesToImportDir() + "/loaded");
                    if (!Files.exists(loaded)) {
                        Files.createDirectories(loaded);
                    }
                    // then move it somewhere useful
                    long timestamp = System.currentTimeMillis();
                    Files.copy(zipforpendinguploadresult, loaded.resolve(timestamp + pu.getFileName() + "results.zip"), StandardCopyOption.REPLACE_EXISTING);
                    // then adjust the pending upload record to have the report
                    pu.setImportResultPath(loaded.resolve(timestamp + pu.getFileName() + "results.zip").toString());
                    pu.setProcessedByUserId(loggedInUser.getUser().getId());
                    pu.setProcessedDate(LocalDateTime.now());
                    pu.setFileName(pu.getFileName() + " - " + "results"); // to make clear to users they'll be downloading results not the source file. See no harm in adjusting this thought perhaps some kind extra field should be used
                    PendingUploadDAO.store(pu);
                    if (loggedInUser.getUser().isAdministrator()) {
                        session.setAttribute(ManageDatabasesController.IMPORTURLSUFFIX, "?uploadreports=true#3"); // if actually importing will land back on the pending uploads page
                    } else {
                        session.setAttribute(ManageDatabasesController.IMPORTURLSUFFIX, "?uploadreports=true#1");
                    }
                }
                session.setAttribute(ManageDatabasesController.IMPORTRESULT, uploadedFiles);
            } catch (Exception e) {
                uploadedFile.setError(CommonReportUtils.getErrorFromServerSideException(e));
                session.setAttribute(ManageDatabasesController.IMPORTRESULT, Collections.singletonList(uploadedFile));
                e.printStackTrace();
            }
        }).start();
        model.put("id", id);
        // will be nothing if there's no manual paramteres
        model.put("paramspassthrough", lookupValuesForFilesHTML.toString());
        if (actuallyImport) {
            if (loggedInUser.getUser().isAdministrator()) {
                model.addAttribute("targetController", "ManageDatabases");
            } else {
                model.addAttribute("targetController", "UserUpload");
            }
            return "importrunning2";
        } else {
            if (loggedInUser.getBusiness().is2023Design()) {
                return "validationready";
            }
            return "validationready2";
        }

         */
        return jsonError("shouldn't get here");
    }


    private static String userReturn(LoggedInUser loggedInUser, ModelMap model) {
        if (loggedInUser.getBusiness().isNewDesign()) {
            return jsonError((String) model.get("error"));
        }
        return "pendingupload2";

    }

    private static void dealWithComment(PendingUpload pu, String commentId, String commentSave) {
        Comment forBusinessIdAndIdentifierAndTeam = CommentDAO.findForBusinessIdAndIdentifierAndTeam(pu.getBusinessId(), commentId, pu.getTeam());
        if (forBusinessIdAndIdentifierAndTeam != null) {
            if (commentSave.isEmpty()) {
                CommentDAO.removeById(forBusinessIdAndIdentifierAndTeam);
            } else {
                forBusinessIdAndIdentifierAndTeam.setText(commentSave);
                CommentDAO.store(forBusinessIdAndIdentifierAndTeam);
            }
        } else if (!commentSave.isEmpty()) {
            CommentDAO.store(new Comment(0, pu.getBusinessId(), commentId, pu.getTeam(), commentSave));
        }
    }

    // like the one in ManageDatabasesController but for sheets. Started by modifying that but it got messy quickly hence a new function in here . . .
    public static void formatUploadedFilesForSheet(PendingUpload pu, List<UploadedFile> uploadedFiles, Sheet
            sheet) {
        int rowIndex = 0;
        int cellIndex = 0;
        int fileIndex = 0;
        for (UploadedFile uploadedFile : uploadedFiles) {
            Row row = sheet.createRow(rowIndex++);
            List<String> names = new ArrayList<>(uploadedFile.getFileNames()); // copy as I might change it
            for (String name : names) {
                row.createCell(cellIndex++).setCellValue(name);
            }
            if (uploadedFile.isConvertedFromWorksheet()) {
                row.createCell(cellIndex).setCellValue("Converted from worksheet");
            }

            cellIndex = 0;
            sheet.createRow(rowIndex++);
            row = sheet.createRow(rowIndex++);

            for (String key : uploadedFile.getParameters().keySet()) {
                row.createCell(cellIndex++).setCellValue(key);
            }
            cellIndex = 0;
            row = sheet.createRow(rowIndex++);
            for (String key : uploadedFile.getParameters().keySet()) {
                row.createCell(cellIndex++).setCellValue(uploadedFile.getParameter(key));
            }
            cellIndex = 0;
            row = sheet.createRow(rowIndex++);
            row.createCell(cellIndex++).setCellValue("Time to process");
            row.createCell(cellIndex).setCellValue(uploadedFile.getProcessingDuration() + " ms");
            cellIndex = 0;
            row = sheet.createRow(rowIndex++);
            row.createCell(cellIndex++).setCellValue("Time to Number of lines imported");
            row.createCell(cellIndex).setCellValue(uploadedFile.getNoLinesImported());
            cellIndex = 0;
            row = sheet.createRow(rowIndex++);
            row.createCell(cellIndex++).setCellValue("Number of values adjusted");
            row.createCell(cellIndex).setCellValue(uploadedFile.getNoValuesAdjusted());
            cellIndex = 0;
            row = sheet.createRow(rowIndex++);
            row.createCell(cellIndex++).setCellValue("Number of names adjusted");
            row.createCell(cellIndex).setCellValue(uploadedFile.getNoNamesAdjusted());

            cellIndex = 0;
            sheet.createRow(rowIndex++);
            row = sheet.createRow(rowIndex++);

            if (uploadedFile.getTopHeadings() != null && !uploadedFile.getTopHeadings().isEmpty()) {
                row.createCell(cellIndex).setCellValue("Top headings");
                for (RowColumn rowColumn : uploadedFile.getTopHeadings().keySet()) {
                    cellIndex = 0;
                    row = sheet.createRow(rowIndex++);
                    row.createCell(cellIndex++).setCellValue(BookUtils.rangeToText(rowColumn.getRow(), rowColumn.getColumn()));
                    row.createCell(cellIndex).setCellValue(uploadedFile.getTopHeadings().get(rowColumn));
                }
                cellIndex = 0;
                row = sheet.createRow(rowIndex++);
            }

            if (uploadedFile.getHeadingsByFileHeadingsWithInterimLookup() != null) {
                row.createCell(cellIndex).setCellValue("Headings with file headings");
                cellIndex = 0;
                row = sheet.createRow(rowIndex++);
                Collection<List<String>> toShow;
                if (uploadedFile.getFileHeadings() != null) {
                    toShow = uploadedFile.getFileHeadings();
                } else {
                    toShow = uploadedFile.getHeadingsByFileHeadingsWithInterimLookup().keySet();
                }

                for (List<String> fileHeading : toShow) {
                    for (String subHeading : fileHeading) {
                        row.createCell(cellIndex++).setCellValue(subHeading);
                    }
                    HeadingWithInterimLookup headingWithInterimLookup = uploadedFile.getHeadingsByFileHeadingsWithInterimLookup().get(fileHeading);
                    if (headingWithInterimLookup != null) {
                        if (headingWithInterimLookup.getInterimLookup() != null) {
                            row.createCell(cellIndex++).setCellValue(headingWithInterimLookup.getInterimLookup());
                        }
                        row.createCell(cellIndex).setCellValue(headingWithInterimLookup.getInterimLookup());
                    } else {
                        row.createCell(cellIndex).setCellValue("** UNUSED **");
                    }
                    cellIndex = 0;
                    row = sheet.createRow(rowIndex++);
                }
            }

            if (uploadedFile.getHeadingsNoFileHeadingsWithInterimLookup() != null) {
                row = sheet.createRow(rowIndex++);
                row.createCell(cellIndex).setCellValue("Headings without file headings");
                cellIndex = 0;
                row = sheet.createRow(rowIndex++);
                for (HeadingWithInterimLookup headingWithInterimLookup : uploadedFile.getHeadingsNoFileHeadingsWithInterimLookup()) {
                    if (headingWithInterimLookup.getInterimLookup() != null) { // it could be null now as we support a non file heading azquo heading on the Import Model sheet
                        row.createCell(cellIndex++).setCellValue(headingWithInterimLookup.getInterimLookup());
                    }
                    row.createCell(cellIndex).setCellValue(headingWithInterimLookup.getHeading());
                    cellIndex = 0;
                    row = sheet.createRow(rowIndex++);
                }
            }

            if (uploadedFile.getSimpleHeadings() != null) {
                row = sheet.createRow(rowIndex++);
                row.createCell(cellIndex).setCellValue("Simple Headings");
                cellIndex = 0;
                row = sheet.createRow(rowIndex++);
                for (String heading : uploadedFile.getSimpleHeadings()) {
                    row.createCell(cellIndex).setCellValue(heading);
                    cellIndex = 0;
                    row = sheet.createRow(rowIndex++);
                }
            }

            if (!uploadedFile.getLinesRejected().isEmpty()) {
                row = sheet.createRow(rowIndex++);
                row.createCell(cellIndex).setCellValue("Line Errors");
                cellIndex = 0;
                int maxHeadingsDepth = 0;
                if (uploadedFile.getFileHeadings() != null) {
                    for (List<String> headingCol : uploadedFile.getFileHeadings()) {
                        if (headingCol.size() > maxHeadingsDepth) {
                            maxHeadingsDepth = headingCol.size();
                        }
                    }
                    // slightly janky looking, the key is to put in the headings as they are in the file - the catch is that the entries in file headings can be of variable size
                    if (maxHeadingsDepth > 1) {
                        for (int i = maxHeadingsDepth; i > 1; i--) {
                            row = sheet.createRow(rowIndex++);
                            row.createCell(cellIndex++).setCellValue("");
                            row.createCell(cellIndex++).setCellValue("");
                            for (List<String> headingCol : uploadedFile.getFileHeadings()) {
                                if (headingCol.size() >= i) {
                                    row.createCell(cellIndex++).setCellValue(headingCol.get(headingCol.size() - i));// think that's right . . .
                                } else {
                                    row.createCell(cellIndex++).setCellValue("");
                                }
                            }
                        }
                    }
                    row = sheet.createRow(rowIndex++);
                    row.createCell(cellIndex++).setCellValue("Error");
                    row.createCell(cellIndex++).setCellValue("#");
                    for (List<String> headingCol : uploadedFile.getFileHeadings()) {
                        if (!headingCol.isEmpty()) {
                            row.createCell(cellIndex++).setCellValue(headingCol.get(headingCol.size() - 1));// think that's right . . .
                        } else {
                            row.createCell(cellIndex++).setCellValue("");
                        }
                    }
                } else {
                    row = sheet.createRow(rowIndex++);
                    row.createCell(cellIndex++).setCellValue("Error");
                    row.createCell(cellIndex++).setCellValue("#");
                }

                for (UploadedFile.RejectedLine lineRejected : uploadedFile.getLinesRejected()) {
                    cellIndex = 0;
                    row = sheet.createRow(rowIndex++);
                    row.createCell(cellIndex++).setCellValue(lineRejected.getErrors());
                    row.createCell(cellIndex++).setCellValue(lineRejected.getLineNo());
                    String[] split = lineRejected.getLine().split("\t");
                    for (String cell : split) {
                        row.createCell(cellIndex++).setCellValue(cell);
                    }
                    cellIndex = 0;
                    sheet.createRow(rowIndex++);
                }
            }
            if (uploadedFile.getPostProcessingResult() != null) {
                cellIndex = 0;
                row = sheet.createRow(rowIndex++);
                row.createCell(cellIndex++).setCellValue("Post processing result");
                row.createCell(cellIndex).setCellValue(uploadedFile.getPostProcessingResult());
                cellIndex = 0;
                sheet.createRow(rowIndex++);
            }
            // now the warning lines that will be a little more complex and have tickboxes
            // We're going to need descriptions of the errors, put them all in a set
            Set<String> errorsSet = new HashSet<>();
            for (UploadedFile.WarningLine warningLine : uploadedFile.getWarningLines()) {
                errorsSet.addAll(warningLine.getErrors().keySet());
            }
            List<String> errors = new ArrayList<>(errorsSet);// consistent ordering - maybe not 100% necessary but best to be safe
            rowIndex = lineWarningsForSheet(pu, uploadedFile, rowIndex, sheet, errors, true, null, fileIndex);
            if (uploadedFile.getError() != null) {
                cellIndex = 0;
                row = sheet.createRow(rowIndex++);
                row.createCell(cellIndex++).setCellValue("ERROR");
                row.createCell(cellIndex).setCellValue(uploadedFile.getError());
                cellIndex = 0;
                sheet.createRow(rowIndex++);
            }

            if (!uploadedFile.isDataModified()) {
                cellIndex = 0;
                row = sheet.createRow(rowIndex++);
                if (uploadedFile.isNoData()) {
                    row.createCell(cellIndex).setCellValue("NO DATA");
                } else {
                    row.createCell(cellIndex).setCellValue("NO DATA MODIFIED");
                }
                cellIndex = 0;
                sheet.createRow(rowIndex++);
            }
            fileIndex++;
        }
    }

    // overview that will hopefully be useful to the user
    private static void summaryUploadFeedbackForSheet(List<UploadedFile> uploadedFiles, Sheet
            sheet, PendingUpload pu) {
        int rowIndex = 0;
        int cellIndex;
        int fileIndex = 0;
        for (UploadedFile uploadedFile : uploadedFiles) {
            cellIndex = 0;
            Row row = sheet.createRow(rowIndex++);
            List<String> names = new ArrayList<>(uploadedFile.getFileNames()); // copy as I might change it
            for (String name : names) {
                row.createCell(cellIndex++).setCellValue(name);
            }
            if (uploadedFile.getError() != null) {
                row.createCell(cellIndex).setCellValue(uploadedFile.getError());
                sheet.createRow(rowIndex++);
                sheet.createRow(rowIndex++);
                continue; // necessary?
            }

            cellIndex = 0;
            sheet.createRow(rowIndex++);
            sheet.createRow(rowIndex++);
            // need to jam in parameters. Firstly as it's useful info and secondly as it will be needed if doing a reimport based on this file
            if (uploadedFile.getParameters() != null && !uploadedFile.getParameters().isEmpty()) {
                row = sheet.createRow(rowIndex++);
                row.createCell(cellIndex).setCellValue(StringLiterals.PARAMETERS);
                for (Map.Entry<String, String> pair : uploadedFile.getParameters().entrySet()) {
                    cellIndex = 0;
                    row = sheet.createRow(rowIndex++);
                    row.createCell(cellIndex++).setCellValue(pair.getKey());
                    if (pair.getKey().equalsIgnoreCase(ImportService.IMPORTVERSION)) { // then knock off any trailing numbers
                        String value = pair.getValue();
                        String end = value.substring(value.length() - 1); // should be last char
                        if (NumberUtils.isNumber(end)) {
                            value = value.substring(0, value.length() - 1);
                        }
                        row.createCell(cellIndex).setCellValue(value);
                    } else {
                        row.createCell(cellIndex).setCellValue(pair.getValue());
                    }
                }
                cellIndex = 0;
                sheet.createRow(rowIndex++);
                sheet.createRow(rowIndex++);
            }

            // We're going to need descriptions of the errors, put them all in a set
            Set<String> errorsSet = new HashSet<>();
            for (UploadedFile.WarningLine warningLine : uploadedFile.getWarningLines()) {
                errorsSet.addAll(warningLine.getErrors().keySet());
            }
            List<String> errors = new ArrayList<>(errorsSet);// consistent ordering - maybe not 100% necessary but best to be safe
            rowIndex = lineWarningsForSheet(pu, uploadedFile, rowIndex, sheet, errors, true, null, fileIndex);
            if (uploadedFile.getIgnoreLinesValues() != null) {
                row = sheet.createRow(rowIndex++);
                row.createCell(cellIndex).setCellValue(StringLiterals.MANUALLYREJECTEDLINES);
                // jumping the cell index across align it with the table above
                cellIndex = errors.size();
                row = sheet.createRow(rowIndex++);
                row.createCell(cellIndex++).setCellValue("Comment");
                row.createCell(cellIndex).setCellValue("#");
                cellIndex = errors.size();
                row = sheet.createRow(rowIndex++);
                ArrayList<Integer> sort = new ArrayList<>(uploadedFile.getIgnoreLinesValues().keySet());
                Collections.sort(sort);
                for (Integer lineNo : sort) {
                    // ok try to get comments now we have a line reference
                    // don't need to remove " from the identifier, it should have been zapped already
                    Comment comment = CommentDAO.findForBusinessIdAndIdentifierAndTeam(pu.getBusinessId(), uploadedFile.getIgnoreLines().get(lineNo), pu.getTeam());
                    row.createCell(cellIndex++).setCellValue(comment != null ? comment.getText() : "");
                    row.createCell(cellIndex++).setCellValue(lineNo);
                    String[] split = uploadedFile.getIgnoreLinesValues().get(lineNo).split("\t");
                    for (String cell : split) {
                        // redundant and it stops things being lined up
                        if (!cell.startsWith(StringLiterals.DELIBERATELYSKIPPINGLINE)) {
                            row.createCell(cellIndex++).setCellValue(cell);
                        }
                    }
                    cellIndex = errors.size();
                    row = sheet.createRow(rowIndex++);
                }
            }
            fileIndex++;
        }
    }

    private static int lineWarningsForSheet(PendingUpload pu, UploadedFile uploadedFile, int rowIndex, Sheet
            sheet, List<String> errors, boolean addHeadings, List<String> paramsHeadings, int fileIndex) {
        if (!uploadedFile.getWarningLines().isEmpty()) {
            int cellIndex = 0;
            Row row;
            if (addHeadings) {
                row = sheet.createRow(rowIndex++);
                row.createCell(cellIndex).setCellValue("Line Warnings" + (paramsHeadings != null ? ", sorting rows below the headings is fine but please do not modify data outside the first two columns, this may confuse the system." : ""));
                cellIndex = 0;
                row = sheet.createRow(rowIndex++);
                for (String error : errors) {
                    row.createCell(cellIndex).setCellValue(error);
                    cellIndex = 0;
                    row = sheet.createRow(rowIndex++);
                }
                int maxHeadingsDepth = 0;
                if (uploadedFile.getFileHeadings() != null) {
                    for (List<String> headingCol : uploadedFile.getFileHeadings()) {
                        if (headingCol.size() > maxHeadingsDepth) {
                            maxHeadingsDepth = headingCol.size();
                        }
                    }
                    // slightly janky looking, the key is to put in the headings as they are in the file - the catch is that the entries in file headings can be of variable size
                    if (maxHeadingsDepth > 1) {
                        for (int i = maxHeadingsDepth; i > 1; i--) {
                            row = sheet.createRow(rowIndex++);
                            row.createCell(cellIndex++).setCellValue("");
                            row.createCell(cellIndex++).setCellValue("");
                            for (List<String> headingCol : uploadedFile.getFileHeadings()) {
                                if (headingCol.size() >= i) {
                                    row.createCell(cellIndex++).setCellValue(headingCol.get(headingCol.size() - i));// think that's right . . .
                                } else {
                                    row.createCell(cellIndex++).setCellValue("");
                                }
                            }
                        }
                    }
                    row = sheet.createRow(rowIndex++);
                    if (paramsHeadings != null) {
                        row.createCell(cellIndex++).setCellValue("Load");
                    }
                    row.createCell(cellIndex++).setCellValue("Comment");
                    if (paramsHeadings != null) {
                        row.createCell(cellIndex++).setCellValue("File");
                        for (String param : paramsHeadings) {
                            row.createCell(cellIndex++).setCellValue(param);
                        }
                        row.createCell(cellIndex++).setCellValue("Identifier");
                    }
                    for (String error : errors) {
                        if (error.contains("[") && error.indexOf("]", error.indexOf("[")) > 0) {
                            error = error.substring(error.indexOf("[") + 1, error.indexOf("]", error.indexOf("[")));
                        }
                        row.createCell(cellIndex++).setCellValue(error);
                    }
                    if (paramsHeadings != null) {
                        row.createCell(cellIndex++).setCellValue("File Index");
                    }
                    row.createCell(cellIndex++).setCellValue("Line No");
                    for (List<String> headingCol : uploadedFile.getFileHeadings()) {
                        if (!headingCol.isEmpty()) {
                            row.createCell(cellIndex++).setCellValue(headingCol.get(headingCol.size() - 1));// think that's right . . .
                        } else {
                            row.createCell(cellIndex++).setCellValue("");
                        }
                    }
                } else {
                    row = sheet.createRow(rowIndex++);
                    if (paramsHeadings != null) {
                        row.createCell(cellIndex++).setCellValue("Load");
                    }
                    row.createCell(cellIndex++).setCellValue("Comment");
                    if (paramsHeadings != null) {
                        row.createCell(cellIndex++).setCellValue("File");
                        for (String param : paramsHeadings) {
                            row.createCell(cellIndex++).setCellValue(param);
                        }
                        row.createCell(cellIndex++).setCellValue("Identifier");
                    }
                    for (String error : errors) {
                        if (error.contains("[") && error.indexOf("]", error.indexOf("[")) > 0) {
                            error = error.substring(error.indexOf("[") + 1, error.indexOf("]", error.indexOf("[")));
                        }
                        row.createCell(cellIndex++).setCellValue(error);
                    }
                    if (paramsHeadings != null) {
                        row.createCell(cellIndex++).setCellValue("File Index");
                    }
                    row.createCell(cellIndex++).setCellValue("Line No");
                }
                cellIndex = 0;
                row = sheet.createRow(rowIndex++);
            } else {
                row = sheet.getRow(sheet.getLastRowNum());
            }

            for (UploadedFile.WarningLine warningLine : uploadedFile.getWarningLines()) {
                if (paramsHeadings != null) {
                    row.createCell(cellIndex++).setCellValue(""); // empty load cell, expects a x or maybe anything?
                }
                String commentValue = "";
                Comment comment = CommentDAO.findForBusinessIdAndIdentifierAndTeam(pu.getBusinessId(), warningLine.getIdentifier().replace("\"", ""), pu.getTeam());
                if (comment != null) {
                    commentValue = comment.getText();
                }
                row.createCell(cellIndex++).setCellValue(commentValue);
                if (paramsHeadings != null) {
                    row.createCell(cellIndex++).setCellValue(uploadedFile.getFileNamesAsString());
                    for (String param : paramsHeadings) {
                        row.createCell(cellIndex++).setCellValue(uploadedFile.getParameter(param) != null ? uploadedFile.getParameter(param) : "");
                    }
                    row.createCell(cellIndex++).setCellValue(warningLine.getIdentifier().replace("\"", ""));
                }

                for (String error : errors) {
                    row.createCell(cellIndex++).setCellValue(warningLine.getErrors().getOrDefault(error, ""));
                }
                if (paramsHeadings != null) {
                    row.createCell(cellIndex++).setCellValue(fileIndex);
                }
                row.createCell(cellIndex++).setCellValue(warningLine.getLineNo());
                String[] split = warningLine.getLine().split("\t");
                for (String cell : split) {
                    row.createCell(cellIndex++).setCellValue(cell);
                }
                cellIndex = 0;
                row = sheet.createRow(rowIndex++);
            }
        }
        return rowIndex;
    }

    public static String jsonAction(String error) {
        return "{\"action\":\"" + error + "\"}";
    }


    public static String jsonError(String error) {
        return "{\"error\":\"" + error + "\"}";
    }


}
