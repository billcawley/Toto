package com.azquo.admin;

import com.azquo.admin.database.Database;
import com.azquo.admin.database.DatabaseDAO;
import com.azquo.admin.onlinereport.*;
import com.azquo.admin.user.UserDAO;
import com.azquo.dataimport.*;
import com.azquo.spreadsheet.LoggedInUser;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.openxml4j.opc.OPCPackage;

import javax.servlet.http.HttpServletRequest;
import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;
import java.util.*;
import java.util.List;


public class StorybookService {

    public static class DisplaySpec{
        Object records;
        int startRecord;
        int recordsToShow;
        boolean showFilter;
        boolean allowNew;
        String recordClass;
        boolean recordsOnly;
        String filterString;

        public DisplaySpec(Object records, int recordsToShow, boolean showFilter, boolean allowNew, String recordClass, boolean recordsOnly){
            this.records = records;
            this.startRecord = 0;
            this.recordsToShow = recordsToShow;
            this.showFilter = showFilter;
            this.allowNew = allowNew;
            this.recordClass = recordClass;
            this.recordsOnly =recordsOnly;
            this.filterString = "";
        }
    }


    static DateTimeFormatter sdf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public static class RecentlyViewed{
        String name;
        String database;
        LocalDateTime time;
        int id;

        RecentlyViewed(String name, String database,LocalDateTime time, int id){
            this.name = name;
            this.database = database;
            this.time = time;
            this.id = id;
        }

    }

    public static class FieldSpec {
        String name;
        String type;
        String heading;

        FieldSpec(String name, String type, String heading) {
            this.name = name;
            this.type = type;
            this.heading = heading;
        }
    }

    public static class TableSpec {
        List<FieldSpec> fieldSpecs;
        List<MenuItem> menu;

        TableSpec(List<FieldSpec> fieldSpecs, List<MenuItem> menu) {
            this.fieldSpecs = fieldSpecs;
            this.menu = menu;
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

    private static List<RecentlyViewed>getRecentActivities(LoggedInUser loggedInUser) {
        List<UserActivity> reportsLoaded =  UserActivityDAO.findMostRecentPeportsForUserAndBusinessId(loggedInUser.getUser().getBusinessId(), loggedInUser.getUser().getEmail(), 3);
        List<RecentlyViewed> recentlyViewed = new ArrayList<>();
        for (UserActivity reportLoaded:reportsLoaded){
            String reportName = reportLoaded.getParameters().get("Report");
            recentlyViewed.add(new RecentlyViewed(reportName,DatabaseDAO.findById(reportLoaded.getDatabaseId()).getName(), reportLoaded.getTimeStamp(), OnlineReportDAO.findForNameAndBusinessId(reportName,loggedInUser.getBusiness().getId()).getId()));
        }
        return recentlyViewed;
    }



    private static List<OnlineReport>loadReports(LoggedInUser loggedInUser) {
        return AdminService.getReportList(loggedInUser, true);
    }

    private static List<UploadRecord.UploadRecordForDisplay>loadImports(LoggedInUser loggedInUser){
        return AdminService.getUploadRecordsForDisplayForBusinessWithBasicSecurity(loggedInUser,"",false);//no auto loads
    }







    public static String getJson(LoggedInUser loggedInUser, HttpServletRequest request) {
        ImportTemplateData importTemplateData = loadMetaData(loggedInUser, request);
        String page = loggedInUser.getCurrentPageInfo();
        if (page==null){
            page = "overview";
        }else{
            page = page.substring(page.indexOf("page=") + 5);
        }
        if (page.endsWith("Schedules")){
            page = page.replace("Schedules"," Schedules");
        }
        final ObjectMapper jacksonMapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        jacksonMapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
        Map<String, Object> toSend = new HashMap<>();
        try {
            Map<String, DisplaySpec> chunks = new LinkedHashMap<>();
            if ("Overview".equals(page)) {
                chunks.put("Recently Viewed",new DisplaySpec(getRecentActivities(loggedInUser), 3,false,false,"az-record-cards", true));
                chunks.put("Reports", new DisplaySpec(loadReports(loggedInUser),5,true,true,"az-table", false));
                chunks.put("Imports", new DisplaySpec(loadImports(loggedInUser),3,true, true, "az-table", false));
                chunks.put("Databases", new DisplaySpec(DatabaseDAO.findForBusinessId(loggedInUser.getBusiness().getId()), 0, true, true, "az-table", false));

            }else if("Reports".equals(page)){
                chunks.put(page,new DisplaySpec(loadReports(loggedInUser),10, true, true, "az-table", false));
            }else if("Imports".equals(page)){
                chunks.put(page, new DisplaySpec(loadImports(loggedInUser), 10, true, true, "az-table", false));
            }else if("Databases".equals(page)){
                chunks.put(page, new DisplaySpec(DatabaseDAO.findForBusinessId(loggedInUser.getBusiness().getId()), 10, true, true, "az-table", false));
            }else if("Users".equals(page)){
                chunks.put(page, new DisplaySpec(UserDAO.findForBusinessId(loggedInUser.getBusiness().getId()), 10, true, true, "az-table", false));
            }else if("Import Schedules".equals(page)){
                chunks.put(page, new DisplaySpec(ImportScheduleDAO.findForBusinessId(loggedInUser.getBusiness().getId()), 10, true, true, "az-table", false));
            }else if("Report Schedules".equals(page)){
                List<Database> databases = DatabaseDAO.findForBusinessId(loggedInUser.getBusiness().getId());
                List<ReportSchedule> reportSchedules = new ArrayList<>();
                for (Database db:databases){
                    reportSchedules.addAll(ReportScheduleDAO.findForDatabaseId(db.getId()));
                }
                chunks.put(page, new DisplaySpec(reportSchedules, 10, true, true, "az-table", false));
            }


            toSend.put("pagedata", importTemplateData);
            toSend.put("tables", chunks);
            toSend.put("tableSpecs", createStorybookMetaData());
            toSend.put("icons", getIconList());
            toSend.put("mainmenu", getMainMenu());
            toSend.put("secondarymenu",getSecondaryMenu());
            String lastdatabase = "";
            if (loggedInUser.getDatabase()!=null){
                lastdatabase = loggedInUser.getDatabase().getName();
            }
            toSend.put("lastdatabase",lastdatabase);
            return jacksonMapper.writeValueAsString(toSend);



    } catch (Exception e) {
        }
        return jsonError("error in loading " + page);
    }




    public static List<MenuItem> getMainMenu(){
        //THIS IS TEMPORARY - SHOULD USE METADATA
        Map<String,String> icons = getIconList();
        List<MenuItem> menuItems = new ArrayList<>();
        menuItems.add(new MenuItem("Overview", "/api/ManageReports?table=Overview", icons.get("overview")));
        menuItems.add(new MenuItem("Reports", "/api/ManageReports?table=Reports",icons.get("reports")));
        menuItems.add(new MenuItem("Search Database", "/searchdb", icons.get("search")));
        menuItems.add(new MenuItem("Imports", "/api/ManageReports?table=Imports", icons.get("imports")));
        menuItems.add(new MenuItem("Databases", "/api/ManageReports?table=Databases", icons.get("databases")));
        menuItems.add(new MenuItem("Users", "/api/ManageReports?table=Users", icons.get("databases")));
        menuItems.add(new MenuItem(" Import Schedules", "/api/ManageReports?table=ImportSchedules", icons.get("imports")));
        menuItems.add(new MenuItem("Report Schedules", "/api/ManageReports?table=ReportSchedules",icons.get("imports")));
        menuItems.add(new MenuItem("Log off", "/api/Login/?logoff=true",icons.get("imports")));
        return menuItems;

    }



    public static List<MenuItem>getSecondaryMenu(){
        return new ArrayList<>();
    }


    public static ImportTemplateData loadMetaData(LoggedInUser loggedInUser, HttpServletRequest request){
        try {
            //the metadata for each business can be loaded separately.   Maybe there should be metdata for databases....
            ImportTemplate importTemplate = ImportTemplateDAO.findForNameBeginningAndBusinessId(loggedInUser.getBusiness().getBusinessName() + " metadata.xlsx", loggedInUser.getUser().getBusinessId());
            if (importTemplate==null){
                // if there is no metadata, use default
                OPCPackage opcPackage = OPCPackage.open(request.getSession().getServletContext().getResourceAsStream("/WEB-INF/Default Metadata.xlsx"));
                return ImportService.readTemplateFile(opcPackage);

            }
            return ImportService.getImportTemplateData(importTemplate, loggedInUser);
        }catch(Exception e){
            System.out.println(e.getStackTrace());
        }
        return null;

    }


    public static Map<String,TableSpec> createStorybookMetaData() {



        Map<String,String> icons = getIconList();

        Map<String, TableSpec> tableSpecs = new HashMap<>();
        List<FieldSpec> fieldSpecs = new ArrayList<>();

        //RECENTLY VIEWED
        fieldSpecs.add(new FieldSpec("name", "show","Name"));
        fieldSpecs.add(new FieldSpec("database", "show","Database"));
        fieldSpecs.add(new FieldSpec("time", "show","Last seen"));
        List<MenuItem> menu = new ArrayList<>();
        menu.add(new MenuItem("Open","/api/Online?database=DATABASE&reportid=ID",icons.get("open")));
        menu.add(new MenuItem("Download", "/api/DownloadTemplate?reportid=ID",icons.get("download")));
        menu.add(new MenuItem("","",""));
        menu.add(new MenuItem("Delete", "/api/ManageReports/deleteid=ID", icons.get("delete")));
        TableSpec tableSpec = new TableSpec(fieldSpecs, menu);
        tableSpecs.put("Recently Viewed", tableSpec);

        //REPORTS
        fieldSpecs = new ArrayList<>();
        fieldSpecs.add(new FieldSpec("name", "select","Name"));
        fieldSpecs.add(new FieldSpec("database", "show","Database"));
        fieldSpecs.add(new FieldSpec("author", "show","Author"));
        menu = new ArrayList<>();
        menu.add(new MenuItem("Open","/api/Online?database=DATABASE&reportid=ID",icons.get("open")));
        menu.add(new MenuItem("Download", "/api/DownloadTemplate?reportid=ID",icons.get("download")));
        menu.add(new MenuItem("","",""));
        menu.add(new MenuItem("Delete", "/api/ManageReports/deleteid=ID", icons.get("delete")));
        tableSpec = new TableSpec(fieldSpecs, menu);
        tableSpecs.put("Reports", tableSpec);

        //USERS
        fieldSpecs = new ArrayList<>();
        fieldSpecs.add(new FieldSpec("name","show","User"));
        fieldSpecs.add(new FieldSpec("email","show","Email"));
        fieldSpecs.add(new FieldSpec("endDate","date","End Date"));
        fieldSpecs.add(new FieldSpec("status","show","Role"));
        fieldSpecs.add(new FieldSpec("selections","show","Selections"));
        fieldSpecs.add(new FieldSpec("recentActivity","show","Recent Activity"));
        menu = new ArrayList<>();
        menu.add(new MenuItem("Edit", "/api/ManageUsers?editid=ID",icons.get("edit")));
        menu.add(new MenuItem("","",""));
        menu.add(new MenuItem("Delete", "/api/ManageUsers/deleteid=ID", icons.get("delete")));
        tableSpecs.put("Users",new TableSpec(fieldSpecs,menu));






        //DATABASES
        fieldSpecs = new ArrayList<>();
        fieldSpecs.add(new FieldSpec("name", "show","Name"));
        fieldSpecs.add(new FieldSpec("persistenceName", "show","Persistence Name"));
        fieldSpecs.add(new FieldSpec("nameCount", "show","Name Count"));
        fieldSpecs.add(new FieldSpec("valueCount", "show","Value Count"));
        fieldSpecs.add(new FieldSpec("created", "date","Created Date"));
        fieldSpecs.add(new FieldSpec("lastProvenance", "show","Last amended"));
        fieldSpecs.add(new FieldSpec("autoBackup", "show","Auto Backup"));
        menu = new ArrayList<>();
        menu.add(new MenuItem("Inspect", "/api/Jstree?op=new&database=DATABASE", icons.get("inspect")));
        menu.add(new MenuItem("Download", "/api/DownloadBackup>id=ID", icons.get("download")));
        menu.add(new MenuItem("Unload", "/api/ManageDatabases/unloadid=ID", icons.get("unload")));
        menu.add(new MenuItem("","",""));
        menu.add(new MenuItem("Empty", "/api/ManageDataqbases?emptyid=ID", icons.get("empty")));
        menu.add(new MenuItem("Delete", "/api/ManageDatabases/deleteid=ID", icons.get("delete")));
        tableSpecs.put("Databases", new TableSpec(fieldSpecs, menu));

        //IMPORT SCHEDULES
        fieldSpecs = new ArrayList<>();
        fieldSpecs.add(new FieldSpec("name", "show","Name"));
        fieldSpecs.add(new FieldSpec("database", "show","Database"));
        fieldSpecs.add(new FieldSpec("user", "show","Author"));
        fieldSpecs.add(new FieldSpec("nextDate", "date","Next Due"));
        fieldSpecs.add(new FieldSpec("frequency", "show","Frequency"));
        menu = new ArrayList<>();
        menu.add(new MenuItem("Edit", "/api/ManageImportSchedules?editid=ID",icons.get("edit")));
        menu.add(new MenuItem("","",""));
        menu.add(new MenuItem("Delete", "/api/ManageImportSchedules?deleteid=ID", icons.get("delete")));
        tableSpecs.put("Import Schedules", new TableSpec(fieldSpecs, menu));

        //REPORT SCHEDULES
        fieldSpecs = new ArrayList<>();
        fieldSpecs.add(new FieldSpec("database", "show","Database"));
        fieldSpecs.add(new FieldSpec("recipients", "show","Recipients"));
        fieldSpecs.add(new FieldSpec("nextDue", "date","Next Due"));
        fieldSpecs.add(new FieldSpec("period", "show","Frequency"));
        fieldSpecs.add(new FieldSpec("report", "show","Report name"));
        fieldSpecs.add(new FieldSpec("parameters", "show","Parameters"));
        menu = new ArrayList<>();
        menu.add(new MenuItem("Edit", "/api/ManageReportSchedules?editid=ID",icons.get("edit")));
        menu.add(new MenuItem("","",""));
        menu.add(new MenuItem("Delete", "/api/ManageReportSchedules?deleteid=ID", icons.get("delete")));
        tableSpecs.put("Report Schedules", new TableSpec(fieldSpecs, menu));


        //IMPORTS
        fieldSpecs = new ArrayList<>();
        fieldSpecs.add(new FieldSpec("fileName", "show","Name"));
        fieldSpecs.add(new FieldSpec("databaseName", "show","Database"));
        fieldSpecs.add(new FieldSpec("userName", "show","Author"));
        fieldSpecs.add(new FieldSpec("formattedDate", "show","Upload date"));
        menu = new ArrayList<>();
        menu.add(new MenuItem("Delete", "/api/ManageDatabases/deleteUploadRecordid=ID", icons.get("delete")));
        tableSpecs.put("Imports", new TableSpec(fieldSpecs, menu));




        return tableSpecs;

    }

     public static String jsonError(String error) {
          return "{\"error\":\"" + error + "\"}";
     }









    public static Map<String,String>getIconList(){
        Map<String,String>iconList = new HashMap<>();
        String heading = "<svg xmlns=\"http://www.w3.org/2000/svg\" fill=\"none\" viewBox=\"0 0 24 24\"" +
                "stroke-width=\"2\" stroke=\"currentColor\" aria-hidden=\"true\">" +
                "<path stroke-linecap=\"round\" stroke-linejoin=\"round\" d=\"";
        String footer = "\"></path></svg>";
        Map<String,String> defs = new HashMap<>();
        defs.put("report","M9 17v-2m3 2v-4m3 4v-6m2 10H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z");
        defs.put("search","M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z");
        defs.put("open","M10 6H6a2 2 0 00-2 2v10a2 2 0 002 2h10a2 2 0 002-2v-4M14 4h6m0 0v6m0-6L10 14");
        defs.put("download","M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4");
        defs.put("delete","M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16");
        defs.put("comment","M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16");
        defs.put("imports","M7 16a4 4 0 01-.88-7.903A5 5 0 1115.9 6L16 6a5 5 0 011 9.9M15 13l-3-3m0 0l-3 3m3-3v12");
        defs.put("import", defs.get("imports"));
        defs.put("recently viewed", defs.get("report"));
        defs.put("settings","M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16");
        defs.put("help","M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16");
        defs.put("import schedules","M3 12l2-2m0 0l7-7 7 7M5 10v10a1 1 0 001 1h3m10-11l2 2m-2-2v10a1 1 0 01-1 1h-3m-6 0a1 1 0 001-1v-4a1 1 0 011-1h2a1 1 0 011 1v4a1 1 0 001 1m-6 0h6");
        defs.put("report schedules","M3 12l2-2m0 0l7-7 7 7M5 10v10a1 1 0 001 1h3m10-11l2 2m-2-2v10a1 1 0 01-1 1h-3m-6 0a1 1 0 001-1v-4a1 1 0 011-1h2a1 1 0 011 1v4a1 1 0 001 1m-6 0h6");
        defs.put("users","M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16");
        defs.put("databases","M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16");
        defs.put("overview","M3 12l2-2m0 0l7-7 7 7M5 10v10a1 1 0 001 1h3m10-11l2 2m-2-2v10a1 1 0 01-1 1h-3m-6 0a1 1 0 001-1v-4a1 1 0 011-1h2a1 1 0 011 1v4a1 1 0 001 1m-6 0h6");
        defs.put("reports","M8 7v8a2 2 0 002 2h6M8 7V5a2 2 0 012-2h4.586a1 1 0 01.707.293l4.414 4.414a1 1 0 01.293.707V15a2 2 0 01-2 2h-2M8 7H6a2 2 0 00-2 2v10a2 2 0 002 2h8a2 2 0 002-2v-2");
        defs.put("showrecords","M4 6h16M4 10h16M4 14h16M4 18h16");
        defs.put("showcards","M4 6a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2H6a2 2 0 01-2-2V6zM14 6a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2h-2a2 2 0 01-2-2V6zM4 16a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2H6a2 2 0 01-2-2v-2zM14 16a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2h-2a2 2 0 01-2-2v-2z");
        defs.put("menu","M12 5v.01M12 12v.01M12 19v.01M12 6a1 1 0 110-2 1 1 0 010 2zm0 7a1 1 0 110-2 1 1 0 010 2zm0 7a1 1 0 110-2 1 1 0 010 2z");
        defs.put("dropdown","M10 3a1 1 0 01.707.293l3 3a1 1 0 01-1.414 1.414L10 5.414 7.707 7.707a1 1 0 01-1.414-1.414l3-3A1 1 0 0110 3zm-3.707 9.293a1 1 0 011.414 0L10 14.586l2.293-2.293a1 1 0 011.414 1.414l-3 3a1 1 0 01-1.414 0l-3-3a1 1 0 010-1.414z");
        for (String def:defs.keySet()){
            iconList.put(def,heading + defs.get(def) + footer);
        }
        return iconList;

    }



}
