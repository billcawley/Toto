package com.azquo.admin;

import com.azquo.admin.database.Database;
import com.azquo.admin.database.DatabaseDAO;
import com.azquo.admin.onlinereport.OnlineReport;
import com.azquo.admin.onlinereport.OnlineReportDAO;
import com.azquo.admin.user.UserDAO;
import com.azquo.dataimport.UploadRecord;
import com.azquo.dataimport.UploadRecordDAO;
import com.azquo.spreadsheet.LoggedInUser;
import com.azquo.spreadsheet.SpreadsheetService;
import com.azquo.spreadsheet.controller.ExcelController;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.zkoss.zk.ui.Page;

import javax.xml.crypto.Data;
import java.awt.*;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;



public class StorybookService {

     static DateTimeFormatter sdf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");


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

    public static class PageSpec {
        List<FieldSpec> fieldSpecs;
        List<String> menu;

        PageSpec(List<FieldSpec> fieldSpecs, List<String> menu) {
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



    public static String getJson(LoggedInUser loggedInUser, String page) {
        try {
            final ObjectMapper jacksonMapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            jacksonMapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
            String toReturn = jsonError(page + " not understood");
            if ("metadata".equals(page)) {
                return createStorybookMetaJson(loggedInUser);
            }
            Map<String, Object>records = new HashMap<>();
            if ("reports".equals(page)) {
                List<OnlineReport> reports = AdminService.getReportList(loggedInUser, true);
                records.put("reports", reports);
             } else if ("imports".equals(page)) {
                List<UploadRecord.UploadRecordForDisplay> imports = AdminService.getUploadRecordsForDisplayForBusinessWithBasicSecurity(loggedInUser,"",false);//no auto loads
                records.put("imports", imports);
                return jacksonMapper.writeValueAsString(records);
             }
            return jacksonMapper.writeValueAsString(records);

        } catch (Exception e) {
        }
        return jsonError("error in loading " + page);
    }




    public static List<MenuItem> getMainMenu(){
        Map<String,String> icons = getIconList();
        List<MenuItem> menuItems = new ArrayList<>();
        menuItems.add(new MenuItem("Overview", "/", icons.get("overview")));
        menuItems.add(new MenuItem("Reports", "/reports?page=reports",icons.get("reports")));
        menuItems.add(new MenuItem("Search Database", "/searchdb", icons.get("search")));
        menuItems.add(new MenuItem("Imports", "reports?page=imports", icons.get("imports")));
        menuItems.add(new MenuItem("Databases", "/reports?page=databases", icons.get("databases")));
        menuItems.add(new MenuItem(" Import Schedules", "/ManageImportSchedules", icons.get("import")));
        menuItems.add(new MenuItem("Report Schedules", "/reports?page=reportschedules",icons.get("import")));
        return menuItems;

    }


    public static List<MenuItem>getSecondaryMenu(){
        return new ArrayList<>();
    }



    public static String createStorybookMetaJson(LoggedInUser loggedInUser) {
        final ObjectMapper jacksonMapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        jacksonMapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
        Map<String, PageSpec> pageSpecs = new HashMap<>();
        List<FieldSpec> fieldSpecs = new ArrayList<>();
        fieldSpecs.add(new FieldSpec("name", "select","Name"));
        fieldSpecs.add(new FieldSpec("database", "show","Database"));
        fieldSpecs.add(new FieldSpec("author", "show","Author"));
         List<String> menu = new ArrayList<>();
        menu.add("Open");
        menu.add("Download");
        menu.add("");
        menu.add("Delete");
        PageSpec pageSpec = new PageSpec(fieldSpecs, menu);
        pageSpecs.put("reports", pageSpec);
        fieldSpecs = new ArrayList<>();
        fieldSpecs.add(new FieldSpec("Name", "show","Name"));
        fieldSpecs.add(new FieldSpec("persistence_name", "show","Persistence Name"));
        fieldSpecs.add(new FieldSpec("names", "show","Name Count"));
        fieldSpecs.add(new FieldSpec("values", "show","Value Count"));
        fieldSpecs.add(new FieldSpec("created", "show","Created Date"));
        fieldSpecs.add(new FieldSpec("last_audit", "show","Last amended"));
        fieldSpecs.add(new FieldSpec("auto-backup", "show","Auto Backup"));
        menu = new ArrayList<>();
        menu.add("Downoad");
        menu.add("");
        menu.add("Delete");
        menu.add("Empty");
        pageSpecs.put("databases", new PageSpec(fieldSpecs, menu));
        fieldSpecs = new ArrayList<>();
        fieldSpecs.add(new FieldSpec("fileName", "show","Name"));
        fieldSpecs.add(new FieldSpec("databaseName", "show","Database"));
        fieldSpecs.add(new FieldSpec("userName", "show","User"));
        fieldSpecs.add(new FieldSpec("formattedDate", "show","Upload date"));
        menu = new ArrayList<>();
        menu.add("Downoad");
        menu.add("");
        menu.add("Delete");
        pageSpecs.put("imports", new PageSpec(fieldSpecs, menu));

        Map<String, Object> toSave = new HashMap<>();
        toSave.put("pageSpecs", pageSpecs);
        toSave.put("icons",getIconList());
        List<Database>databases = DatabaseDAO.findForBusinessId(loggedInUser.getBusiness().getId());
        Map<Integer,String> dbList = new HashMap<>();
        for (Database db:databases){
            dbList.put(db.id, db.getName());
        }
        toSave.put("databases", dbList);
        try {
            String info = jacksonMapper.writeValueAsString(toSave);
            return info;
        }catch(Exception e){
            return jsonError("no metadata");
        }

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
        defs.put("settings","M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16");
        defs.put("help","M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16");
        defs.put("import schedules","M3 12l2-2m0 0l7-7 7 7M5 10v10a1 1 0 001 1h3m10-11l2 2m-2-2v10a1 1 0 01-1 1h-3m-6 0a1 1 0 001-1v-4a1 1 0 011-1h2a1 1 0 011 1v4a1 1 0 001 1m-6 0h6");
        defs.put("report schedules","M3 12l2-2m0 0l7-7 7 7M5 10v10a1 1 0 001 1h3m10-11l2 2m-2-2v10a1 1 0 01-1 1h-3m-6 0a1 1 0 001-1v-4a1 1 0 011-1h2a1 1 0 011 1v4a1 1 0 001 1m-6 0h6");
        defs.put("users","M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16");
        defs.put("databases","M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16");
        defs.put("overview","M3 12l2-2m0 0l7-7 7 7M5 10v10a1 1 0 001 1h3m10-11l2 2m-2-2v10a1 1 0 01-1 1h-3m-6 0a1 1 0 001-1v-4a1 1 0 011-1h2a1 1 0 011 1v4a1 1 0 001 1m-6 0h6");
        defs.put("reports","M3 12l2-2m0 0l7-7 7 7M5 10v10a1 1 0 001 1h3m10-11l2 2m-2-2v10a1 1 0 01-1 1h-3m-6 0a1 1 0 001-1v-4a1 1 0 011-1h2a1 1 0 011 1v4a1 1 0 001 1m-6 0h6");
        defs.put("showrecords","M4 6h16M4 10h16M4 14h16M4 18h16");
        defs.put("showcards","M4 6a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2H6a2 2 0 01-2-2V6zM14 6a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2h-2a2 2 0 01-2-2V6zM4 16a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2H6a2 2 0 01-2-2v-2zM14 16a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2h-2a2 2 0 01-2-2v-2z");
        defs.put("menu","M12 5v.01M12 12v.01M12 19v.01M12 6a1 1 0 110-2 1 1 0 010 2zm0 7a1 1 0 110-2 1 1 0 010 2zm0 7a1 1 0 110-2 1 1 0 010 2z");
        for (String def:defs.keySet()){
            iconList.put(def,heading + defs.get(def) + footer);
        }
        return iconList;

    }



}
