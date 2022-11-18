package com.azquo.admin.controller;

import com.azquo.StringLiterals;
import com.azquo.admin.AdminService;
import com.azquo.admin.business.Business;
import com.azquo.admin.business.BusinessDAO;
import com.azquo.admin.database.Database;
import com.azquo.admin.database.DatabaseDAO;
import com.azquo.admin.onlinereport.ExternalDatabaseConnectionDAO;
import com.azquo.admin.onlinereport.OnlineReport;
import com.azquo.admin.onlinereport.OnlineReportDAO;
import com.azquo.dataimport.*;
import com.azquo.spreadsheet.LoggedInUser;
import com.azquo.spreadsheet.LoginService;
import com.azquo.spreadsheet.SpreadsheetService;
import com.azquo.spreadsheet.controller.CreateExcelForDownloadController;
import com.azquo.spreadsheet.controller.LoginController;
import com.azquo.spreadsheet.controller.OnlineController;
import com.azquo.spreadsheet.transport.UploadedFile;
import com.azquo.spreadsheet.zk.BookUtils;
import net.snowflake.client.jdbc.internal.amazonaws.services.s3.transfer.Upload;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.math.NumberUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.zkoss.poi.openxml4j.opc.OPCPackage;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Copyright (C) 2016 Azquo Ltd.
 * <p>
 * Created by cawley on 24/04/15.
 * <p>
 * importSchedule CRUD.
 */
@Controller
@RequestMapping("/ManageImportSchedules")
public class ManageImportSchedulesController {

    @Autowired
    ServletContext servletContext;

    @RequestMapping
    public String handleRequest(ModelMap model, HttpServletRequest request
            , HttpServletResponse response
            , @RequestParam(value = "newdesign", required = false) String newdesign
            , @RequestParam(value = "editId", required = false) String editId
    )throws Exception {
        return handleRequest(model, request, response,newdesign, editId,null,null,null,null,null,null,null,null,null,null,null,null,null,null);

        /*
        LoggedInUser loggedInUser = (LoggedInUser) request.getSession().getAttribute(LoginController.LOGGED_IN_USER_SESSION);
        // I assume secure until we move to proper spring security
        if (loggedInUser != null && (loggedInUser.getUser().isAdministrator() || loggedInUser.getUser().isDeveloper())) {
            AdminService.setBanner(model, loggedInUser);
            if (newdesign!=null) {
                prepareNewJavascript(servletContext, loggedInUser, model);
                model.put("pageUrl", "/reports");
                return "manageimportschedulesnew";
            }
            model.put("importschedules",ImportScheduleDAO.findForBusinessId(loggedInUser.getBusiness().getId()));
            return "manageimportschedules";
        } else {
            return "redirect:/api/Login";
        }

         */
    }




    final static String[] periods={"never","second","minute","hour","day","week","month"};

      //    private static final Logger logger = Logger.getLogger(ManageimportSchedulesController.class);
     @RequestMapping(headers = "content-type=multipart/*")
    public String handleRequest(ModelMap model, HttpServletRequest request
            , HttpServletResponse response
             , @RequestParam(value = "newdesign", required = false) String newdesign
             , @RequestParam(value = "editId", required = false) String editId
             , @RequestParam(value = "count", required = false) String count
            , @RequestParam(value = "frequency", required = false) String frequency
            , @RequestParam(value = "nextdate", required = false) String nextDate
            , @RequestParam(value = "deleteId", required = false) String deleteId
            , @RequestParam(value = "name", required = false) String name
            , @RequestParam(value = "databaseid", required = false) String databaseId
            , @RequestParam(value = "connectorid", required = false) String connectorId
            , @RequestParam(value = "sql", required = false) String sql
            , @RequestParam(value = "newtemplate", required = false) String template
            , @RequestParam(value = "outputconnectorid", required = false) String outputConnectorId
            , @RequestParam(value = "notes", required = false) String notes
            , @RequestParam(value = "action", required = false) String action
             , @RequestParam(value = "uploadFile", required = false) MultipartFile uploadFile
             , @RequestParam(value = "regex", required = false) String regex

     ) throws Exception {
        LoggedInUser loggedInUser = (LoggedInUser) request.getSession().getAttribute(LoginController.LOGGED_IN_USER_SESSION);
        // added for Ed Broking but could be used by others. As mentioned this should only be used on a private network
        if (loggedInUser == null || !loggedInUser.getUser().isAdministrator()) {
            return "redirect:/api/Login";
        } else {

            StringBuilder error = new StringBuilder();
             List<Database> databases = DatabaseDAO.findForBusinessId(loggedInUser.getBusiness().getId());
                 // api access will give basic crud against importSchedules. There may be some duplication with the edit bit below, deal with that after if it's an issue
            DateTimeFormatter dateTimeFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            if (NumberUtils.isDigits(deleteId)) {
                int toDelete = Integer.parseInt(deleteId);
                ImportScheduleDAO.removeById(ImportScheduleDAO.findById(toDelete));
            }

            if (NumberUtils.isDigits(editId)) {
                ImportSchedule toEdit = ImportScheduleDAO.findById(Integer.parseInt(editId));
                if ("test".equals(request.getParameter("action")) && editId !=null){
                      String toReturn = setupWizard(loggedInUser, uploadFile,toEdit);
                    if (toReturn.startsWith("error:"))       {
                        model.put("error", toReturn.substring(6));
                        return "editimportschedule";
                    }
                    if (loggedInUser.getWizardInfo()!=null && loggedInUser.getWizardInfo().getPreprocessor()!=null){
                        request.setAttribute(OnlineController.BOOK, loggedInUser.getWizardInfo().getPreprocessor());
                    }
                    return toReturn;

                }


                String savedSQL = sql;
                if ("0".equals(connectorId)){
                    savedSQL = regex;

                }
                int dbId = 0;
                if (toEdit!=null){
                    dbId = toEdit.getDatabaseId();
                }
                try{
                    dbId = Integer.parseInt(databaseId);
                }catch(Exception e){
                }
                if (dbId > 0 && toEdit!=null && dbId!=toEdit.getDatabaseId()) {
                    toEdit.setDatabaseId(dbId);
                    ImportScheduleDAO.store(toEdit);
                    error.append("database changed ...<br/>");


                     if (dbId > 0 && (loggedInUser.getDatabase()==null || loggedInUser.getDatabase().getId()!=dbId)){
                         Database db = DatabaseDAO.findById(dbId);
                         LoginService.switchDatabase(loggedInUser, db.getName());
                     }

                 }
                String dbName = "unassigned";
                if (dbId>0){
                    Database db = DatabaseDAO.findById(dbId);
                    dbName = db.getName();
                }
                ImportTemplate importTemplate = ImportTemplateDAO.findForNameAndBusinessId(dbName + " Import Templates.xlsx", loggedInUser.getBusiness().getId());
                List<String>templates = new ArrayList<>();
                templates.add("NEW TEMPLATE");
                if (importTemplate!=null) {
                    try {
                        ImportTemplateData importTemplateData = ImportService.getImportTemplateData(importTemplate, loggedInUser);
                        for (String sheet : importTemplateData.getSheets().keySet()) {
                            templates.add(sheet);
                        }
                    } catch (Exception e) {
                        //no import template
                    }
                }

                // ok check to see if data was submitted
                if (action != null && !action.isEmpty()) {
                   LocalDateTime date = null;
                   try {
                        date = LocalDateTime.parse(nextDate, dateTimeFormat);
                   } catch (DateTimeParseException e) {
                       try{
                           date = LocalDate.parse(nextDate,dateFormatter).atStartOfDay();
                       }catch(Exception e2) {
                           error.append("Next date format not yyyy-MM-dd<br/>");
                       }
                   }
                   if (template==null || template.isEmpty()){
                       error.append("Template required<br/>");
                   }
                   if (name == null || name.isEmpty()) {
                        error.append("Name required<br/>");
                   } else {
                        name = name.trim();
                   }
                   if ((toEdit == null || (toEdit!=null && !toEdit.getName().equals(name))) && ImportScheduleDAO.findForNameAndBusinessId(name, loggedInUser.getBusiness().getId()) != null ) {
                        error.append("Import Schedule Exists<br/>");
                   }
                    if (connectorId.equals("0")) {
                        if (regex == null || regex.isEmpty()) {
                            error.append("regex required<br/>");
                        }
                    }else if(sql == null || !sql.toLowerCase(Locale.ROOT).startsWith("select ")) {
                         error.append("SQL 'select ..' required<br/>");
                    }
                   if (error.length() == 0) {
                        // then store, it might be new
                         if (toEdit == null) {


                            // Have to use  a LocalDate on the parse which is annoying http://stackoverflow.com/questions/27454025/unable-to-obtain-localdatetime-from-temporalaccessor-when-parsing-localdatetime
                            ImportSchedule importSchedule = new ImportSchedule(0, name,Integer.parseInt(count),frequency,date,loggedInUser.getBusiness().getId(),dbId, Integer.parseInt(connectorId), loggedInUser.getUser().getId(),savedSQL, template,  Integer.parseInt(outputConnectorId), notes );
                            ImportScheduleDAO.store(importSchedule);
                        } else {
                            toEdit.setName(name);
                            toEdit.setCount(Integer.parseInt(count));
                            toEdit.setFrequency(frequency);
                            toEdit.setNextDate(date);
                            toEdit.setBusinessId(loggedInUser.getBusiness().getId());
                            toEdit.setDatabaseId(dbId);
                            toEdit.setConnectorId(Integer.parseInt(connectorId));
                            toEdit.setSql(savedSQL);
                            toEdit.setTemplateName(template);
                            toEdit.setOutputConnectorId(Integer.parseInt(outputConnectorId));
                            toEdit.setNotes(notes);
                            ImportScheduleDAO.store(toEdit);
                        }
                       if (newdesign!=null) {
                           prepareNewJavascript(servletContext, loggedInUser, model);
                           model.put("pageUrl", "/reports");
                           return "manageimportschedulesnew";
                       }
                       model.put("importschedules",ImportScheduleDAO.findForBusinessId(loggedInUser.getBusiness().getId()));
                       AdminService.setBanner(model, loggedInUser);
                        return "manageimportschedules";
                   } else {
                        model.put("error", error.toString());
                   }
                   model.put("id", editId);
                    model.put("name", name);
                    model.put("count", count + "");
                    model.put("frequency", frequency);
                    model.put("nextdate", nextDate);
                    model.put("databaseid", dbId);
                    model.put("connectorId",connectorId);
                    model.put("sql", sql);
                    model.put("regex", regex);
                    model.put("template", template);
                    if (!templates.contains(template)){
                        model.put("newtemplate", template);
                    }
                    model.put("outputconnectorid", outputConnectorId);
                    model.put("notes", notes);
                } else {
                    if (toEdit != null) {
                        sql = "";
                        regex = "";
                        if (toEdit.getConnectorId()==0){
                            regex = toEdit.getSql();
                        }else{
                            sql = toEdit.getSql();
                        }
                        if (!templates.contains(toEdit.getTemplateName())){
                            model.put("newtemplate", toEdit.getTemplateName());
                        }
                        model.put("id", toEdit.getId());
                        model.put("name", toEdit.getName());
                        model.put("count", toEdit.getCount() + "");
                        model.put("frequency", toEdit.getFrequency());
                        model.put("databaseid",dbId);
                        model.put("nextdate", dateTimeFormat.format(toEdit.getNextDate()));
                         model.put("connectorid", toEdit.getConnectorId());
                        model.put("sql", sql);
                        model.put("regex", regex);
                        model.put("template", toEdit.getTemplateName());
                        model.put("outputconnectorid", toEdit.getOutputConnectorId());
                        model.put("notes", toEdit.getNotes());
                    } else {
                        model.put("count", 1);
                        model.put("nextdate",dateTimeFormat.format(LocalDateTime.now()));
                        model.put("frequency","day");
                        model.put("databaseid", dbId);
                        model.put("id", "0");
                        model.put("name", name);
                    }
                }

                model.put("databases", databases);
                model.put("templates", templates);
                model.put("connectors", ExternalDatabaseConnectionDAO.findForBusinessId(loggedInUser.getBusiness().getId()));
                model.put("outputconnectors",AdminService.getFileOutputConfigListForBusinessWithBasicSecurity(loggedInUser));
                model.put("periods", periods);
                AdminService.setBanner(model, loggedInUser);
                return "editimportschedule";
            }
            AdminService.setBanner(model, loggedInUser);
            if (newdesign!=null) {
                prepareNewJavascript(servletContext, loggedInUser, model);
                model.put("pageUrl", "/reports");
                return "manageimportschedulesnew";
            }
            model.put("importschedules",ImportScheduleDAO.findForBusinessId(loggedInUser.getBusiness().getId()));
            return "manageimportschedules";
        }
    }
    private static void prepareNewJavascript(ServletContext servletContext, LoggedInUser loggedInUser, ModelMap model){
        try {
            StringBuilder importSchedulesList = new StringBuilder();

            /*
            for (OnlineReport or : reportList){
                reportsList.append("new m({ id: " + or.getId() + ", name: \"" + or.getUntaggedReportName() + "\", database: \"" + or.getDatabase() + "\", author: \"" + or.getAuthor() + "\", description: \"" + or.getExplanation().replace("\n","").replace("\r","") + "\" }),\n");
            }

             */
            List<ImportSchedule>importSchedules = ImportScheduleDAO.findForBusinessId(loggedInUser.getBusiness().getId());
            for (ImportSchedule is:importSchedules){
                importSchedulesList.append("new m({ id: " + is.getId() + ", name: \"" + is.getName() + "\", database: \"" + is.getDatabase() + "\", author: \"" + is.getUser() + "\", description: \"" + is.getNotes().replace("\n","").replace("\r","") + "\" }),\n");

            }

            InputStream resourceAsStream = servletContext.getResourceAsStream("/WEB-INF/includes/importschedules.js");

            StringBuilder importsList = new StringBuilder();
            List<UploadRecord.UploadRecordForDisplay> uploadRecordsForDisplayForBusiness = AdminService.getUploadRecordsForDisplayForBusinessWithBasicSecurity(loggedInUser, null, false);
            for (UploadRecord.UploadRecordForDisplay uploadRecord : uploadRecordsForDisplayForBusiness) {
                importsList.append("new f({\n" +
                        "                        id: " + uploadRecord.id + ",\n" +
                        "                        filename: \"" + uploadRecord.getFileName() + "\",\n" +
                        "                        database: \"" + uploadRecord.getDatabaseName() + "\",\n" +
                        "                        user: \"" + uploadRecord.getUserName() + "\",\n" +
                        "                        date: " + uploadRecord.getDate().toEpochSecond(ZoneOffset.of("Z")) + ",\n" +
                        "                        }),");
            }
            List<Database> databaseList = AdminService.getDatabaseListForBusinessWithBasicSecurity(loggedInUser);
            StringBuilder databasesList = new StringBuilder();
//                new d({ id: 1, name: "Demo1dhdhrt", date: 1546563989 }),
            for (Database d : databaseList) {
                databasesList.append("new d({ id: "+ d.getId() +", name: \"" + d.getName() + "\", date: 0 }),"); // date jammed as zero for the mo, does the JS use it?
            }
            String resource = new BufferedReader(  new InputStreamReader(resourceAsStream, StandardCharsets.UTF_8))
                    .lines()
                    .collect(Collectors.joining("\n"));

            model.put("newappjavascript", "// " +  resource
                    .replace("###IMPORTSLIST###", importsList.toString())
                    .replace("###DATABASESLIST###", databasesList.toString())
                    .replace("###REPORTSLIST###", importSchedulesList.toString()));


        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public static String setupWizard(LoggedInUser loggedInUser, MultipartFile uploadFile, ImportSchedule importSchedule)throws Exception{
        //TODO  - if uploaded file is JSON, get the data.
        String fileName = uploadFile.getOriginalFilename();
        // always move uploaded files now, they'll need to be transferred to the DB server after code split
        File moved = new File(SpreadsheetService.getHomeDir() + "/temp/" + System.currentTimeMillis() + fileName); // timestamp to stop file overwriting
        final Map<String, String> fileNameParams = new HashMap<>();
        ImportService.addFileNameParametersToMap(fileName, fileNameParams);
        String filePath = moved.getAbsolutePath();
        UploadedFile uploadedFile = new UploadedFile(filePath, Collections.singletonList(fileName), fileNameParams, false, false);

        uploadFile.transferTo(moved);
         WizardInfo wizardInfo = new WizardInfo(uploadedFile,importSchedule, null);

        loggedInUser.setWizardInfo(wizardInfo);
        if(importSchedule.getConnectorId()==0){
            wizardInfo.getTemplateParameters().put(ImportWizard.IMPORTFILENAMEREGEX,importSchedule.getSql());
        }
        if (importSchedule.getDatabaseId() > 0){
            LoginService.switchDatabase(loggedInUser, DatabaseDAO.findById(importSchedule.getDatabaseId()).getName());
        }
        wizardInfo.setImportSchedule(importSchedule);
        String toReturn  = ImportWizard.setTemplate(loggedInUser);
        return toReturn;
    }










}