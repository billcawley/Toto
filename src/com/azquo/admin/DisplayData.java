package com.azquo.admin;

import com.azquo.admin.user.Permissions;
import com.azquo.spreadsheet.LoggedInUser;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;



public class DisplayData {


    public static List<Map<String, String>> getStageInfo(String processName) {
        List<Map<String, Object>> stageInfo = new ArrayList<>();
        if (processName.equals("Pending Uploads")) {
            List<Map<String, String>> toReturn = new ArrayList<>();
            stageInfo.add(stageInfo(1, "SELECTION", "Select file to validate", "The file name should be in the form  {coverholder} {claim/premium} {month}.{xlsx/zip}", ".e.g. Allrisks claim Jan-24.zip"));
            stageInfo.add(stageInfo(2, "VIEW RESULTS", "Upload results", "You can click on 'details' to see individual errors, When done, you can click below to load to the live database", ""));
            stageInfo.add(stageInfo(3, "Process finished", "", "Your database is updated", ""));
            return toReturn;
        } else {
            return null;
        }

    }

    public static List<Map<String, String>> getSectionstoShow(String menuitem) {
        List<Map<String, String>> toReturn = new ArrayList<>();
        if (menuitem.equals("Overview")) {
            toReturn.add(sectionInfo("Recently Viewed", 3));
            toReturn.add(sectionInfo("Reports", 5));
            toReturn.add(sectionInfo("Imports", 5));
        } else {
            toReturn.add(sectionInfo(menuitem, 20));
        }
        return toReturn;

    }


    static Map<String, Object> stageInfo(int stageNo, String stageName, String comment, String instructions, String suggestions) {
        Map<String, Object> toReturn = new HashMap<>();
        toReturn.put("stageNo", stageNo);
        toReturn.put("stageName", stageName);
        toReturn.put("comment", comment);
        toReturn.put("instructions", instructions);
        toReturn.put("suggestions", suggestions);
        return toReturn;
    }

    static Map<String, String> sectionInfo(String sectionName, int recordCount) {
        Map<String, String> si = new HashMap<>();
        si.put("sectionName", sectionName);
        si.put("recordCount", recordCount+"");
        return si;
    }

    public static List<Permissions> defaultPermissions() {
        String[][] defaults = {
                {"section", "field_name", "name_on_file", "field_type", "field_value", "readonly"},
                {"Recently Viewed", "Name", "name", "string", "", "Selectable"},
                {"Recently Viewed", "Database", "database", "string", "", "Readonly"},
                {"Recently Viewed", "Last seen", "time", "date", "", "Readonly"},
                {"Recently Viewed", "", "menu", "menu","Download,*,Delete", "Readonly"},
                {"Report", "Name", "reportName", "string", "", "Selectable"},
                {"Report", "Database", "database", "multiselectlist", "database", "ReadWrite"},
                {"Report", "Author", "author", "string", "", "Readonly"},
                {"Report", "Created", "dateCreated", "date", "", "Readonly"},
                {"Report", "", "menu", "menu","Download,*,Delete", "Readonly"},
                {"User", "User", "name", "string", "", "Selectable"},
                {"User", "Email", "email", "string", "", "ReadWrite"},
                {"User", "End Date", "endDate", "date", "", "ReadWrite"},
                {"User", "Role", "status", "combo", "status", "ReadWrite"},
                {"User", "Recent Activity", "recentActivity", "string", "", "Selectable"},
                {"User", "", "menu", "menu","Edit,*,Delete", "Readonly"},
                {"Database", "Name", "name", "string", "", "Selectable"},
                {"Database", "Persistence Name", "persistenceName", "string", "", "Readonly"},
                {"Database", "Name Count", "nameCount", "number", "", "Readonly"},
                {"Database", "Value Count", "valueCount", "number", "", "Readonly"},
                {"Database", "Created Date", "created", "date", "", "Readonly"},
                {"Database", "Last Amended", "lastProvenance", "date", "", "Readonly"},
                {"Database", "Auto Backup", "autoBackup", "boolean", "", "ReadWrite"},
                {"Import", "Name", "fileName", "string", "", "Selectable"},
                {"Import", "Database", "databaseName", "string", "", "Readonly"},
                {"Import", "Author", "userName", "string", "", "Readonly"},
                {"Import", "Upload date", "formattedDate", "string", "", "Readonly"},
                {"Import", "", "menu", "menu","Results,*,Delete", "Readonly"},
                {"Pending Upload", "Name", "fileName", "string", "", "Selectable"},
                {"Pending Upload", "Database", "databaseName", "list", "database", "Readonly"},
                {"Pending Upload", "Author", "createdByUserName", "string", "", "Readonly"},
                {"Pending Upload", "Upload date", "createdDate", "string", "", "Readonly"},
                {"Pending Upload", "", "menu", "menu","Validate,Delete", "Readonly"},
                {"Report Schedule", "Name", "name", "string", "", "Selectable"},
                {"Report Schedule", "Database", "database", "list", "database", "ReadWrite"},
                {"Report Schedule", "Recipients", "recipients", "string", "", "ReadWrite"},
                {"Report Schedule", "Next due", "nextDate", "date", "", "ReadWrite"},
                {"Report Schedule", "Count", "count", "number", "", "ReadWrite"},
                {"Report Schedule", "frequency", "frequency", "string", "", "ReadWrite"},
                {"Report Schedule", "Report Name", "report", "string", "", "ReadWrite"},
                {"Report Schedule", "Parameters", "parameters", "string", "", "ReadWrite"},
                {"Report Schedule", "", "menu", "menu","Edit,*,Delete", "Readonly"},
                {"Main Menu", "Overview", "", "", "", "ReadWrite"},
                {"Main Menu", "Reports", "Report", "", "", "ReadWrite"},
                {"Main Menu", "Search Database", "", "", "", "ReadWrite"},
                {"Main Menu", "Imports", "Import", "", "", "ReadWrite"},
                {"Main Menu", "Pending Uploads", "", "", "", "ReadWrite"},
                {"Main Menu", "Databases", "", "", "", "ReadWrite"},
                {"Main Menu", "Users", "User", "", "", "ReadWrite"},
                {"Main Menu", "Import Schedules", "ImportSchedule", "", "", "ReadWrite"},
                {"Main Menu", "Report Schedules", "ReportSchedule", "", "", "ReadWrite"},
                {"Main Menu", "Log off", "", "", "", "ReadWrite"},
                {"Display Theme", "Background Color", "", "", "#ed1b24", "Readonly"},
                {"Display Theme", "Logo", "", "", "https://www.edbroking.com/sites/all/themes/brand-x/images/logo.jpg", "Readonly"},
                {"Display Theme", "Menu Color", "", "", "#c0392b", "Readonly"},
                {"test2", "", "", "", "", "Readonly"},
                {"test2", "", "", "", "", "Readonly"}};

        List<Permissions> permissions = new ArrayList<>();
        String[] headings = null;
        for (String[] defaultLine : defaults) {
            if (headings == null) {
                headings = defaultLine;
            } else {
                permissions.add(new Permissions(0, 0, "ADMINISTRATOR", defaultLine[0], defaultLine[1], defaultLine[2], defaultLine[3], defaultLine[4], defaultLine[5]));
            }
        }

        return permissions;


    }
}