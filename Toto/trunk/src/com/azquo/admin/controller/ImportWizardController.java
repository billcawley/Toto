package com.azquo.admin.controller;

import com.azquo.admin.AdminService;
import com.azquo.admin.BackupService;
import com.azquo.admin.business.Business;
import com.azquo.admin.database.Database;
import com.azquo.admin.database.DatabaseServerDAO;
import com.azquo.dataimport.*;
import com.azquo.spreadsheet.LoggedInUser;
import com.azquo.spreadsheet.SpreadsheetService;
import com.azquo.spreadsheet.controller.LoginController;
import com.azquo.spreadsheet.transport.UploadedFile;
import io.keikai.api.Exporter;
import io.keikai.api.Exporters;
import io.keikai.api.Importers;
import io.keikai.api.model.Book;
import io.keikai.api.model.Sheet;
import io.keikai.model.SName;
import net.snowflake.client.jdbc.internal.google.api.client.util.ArrayMap;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.math.NumberUtils;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.json.JSONObject;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.zeroturnaround.zip.ZipUtil;

import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;

    /**
     * Copyright (C) 2016 Azquo Ltd.
     * <p>
     * Created by cawley on 21/07/16.
     * <p>
     * Changed 03/10/2018 to use new backup system - old MySQL based one obsolete
     *
     * todo - spinning cog on restoring
     */
    @Controller
    @RequestMapping("/ImportWizard")
    public class ImportWizardController {


//    private static final Logger logger = Logger.getLogger(ManageDatabasesController.class);

        private final SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");


        @RequestMapping
        public String handleRequest(ModelMap model, HttpServletRequest request
             ) {
            LoggedInUser loggedInUser = (LoggedInUser) request.getSession().getAttribute(LoginController.LOGGED_IN_USER_SESSION);
            // I assume secure until we move to proper spring security
            if (loggedInUser != null && (loggedInUser.getUser().isAdministrator() || loggedInUser.getUser().isDeveloper())) {
                AdminService.setBanner(model, loggedInUser);
                fillStages(model,1, null);
                return "importwizard";
            } else {
                return "redirect:/api/Login";
            }
        }

        @RequestMapping(headers = "content-type=multipart/*")
        public String handleRequest(ModelMap model, HttpServletRequest request
                , @RequestParam(value = "uploadFile", required = false) MultipartFile uploadFile
                , @RequestParam(value = "fieldSelected", required = false) String fieldSelected
                , @RequestParam(value = "valueSelected", required = false) String valueSelected
                , @RequestParam(value = "stage", required = false) Integer stage
                , @RequestParam(value = "btnsubmit", required = false) String submit

        ) {

            LoggedInUser loggedInUser = (LoggedInUser) request.getSession().getAttribute(LoginController.LOGGED_IN_USER_SESSION);
            // I assume secure until we move to proper spring security
            if (submit==null) submit="";
            if (loggedInUser == null || !loggedInUser.getUser().isAdministrator()) {
                return "redirect:/api/Login";
            } else {
                  if (stage==null){
                    stage=1;
                 }
                int nextStage = stage;
                if (submit.equals("next")){
                    nextStage = stage + 1;
                    fieldSelected = "";
                }
                if (submit.equals("last")){
                    nextStage = stage - 1;
                    fieldSelected="";
                }
                if ("makedb".equals(submit)){
                    try {
                        ImportWizard.createDB(loggedInUser);
                        return "redirect:/api/ManageReports";
                    }catch(Exception e){
                        model.put("error", e.getMessage());
                    }
                }

                WizardInfo wizardInfo = loggedInUser.getWizardInfo();
                String dataParent = request.getParameter("existingparent");
                String newParent = request.getParameter("newparent");
                if (newParent != null && newParent.length() > 0) {
                    for (String heading : wizardInfo.getFields().keySet()) {
                        if (wizardInfo.getFields().get(heading).getName().equalsIgnoreCase(newParent)) {
                            model.put("error", "Data types cannot be the same as field names");
                            nextStage = stage;
                            fillStages(model, nextStage, wizardInfo);
                            setStageOptions(model, loggedInUser.getWizardInfo(), nextStage, dataParent);
                            return "importwizard";
                        }
                    }
                }
                if (dataParent==null || dataParent.length()==0){
                      dataParent = newParent;
                }
                String newFieldName = request.getParameter("newfieldname");
                if (newFieldName!=null && newFieldName.length()>0){
                    String newFieldAzquoName = request.getParameter("name_new_field");
                    if (newFieldAzquoName == null || newFieldAzquoName.length() == 0){
                        newFieldAzquoName = "new name";
                    }
                    wizardInfo.getFields().put(ImportWizard.HTMLify(newFieldName),new WizardField(newFieldName, newFieldAzquoName,true));
                    nextStage = stage;
                }


                try {
                    boolean importedSavedVersion = false;
                    if (uploadFile != null && !uploadFile.isEmpty()) {
                        //do we need to move it??
                        String fileName = uploadFile.getOriginalFilename();
                        File moved = new File(SpreadsheetService.getHomeDir() + "/temp/" + System.currentTimeMillis() + fileName); // timestamp to stop file overwriting
                        uploadFile.transferTo(moved);
                        if (stage==1) {
                            if(fileName.toLowerCase(Locale.ROOT).contains(".xls")){
                                Map<String,WizardField>wizardFields = new LinkedHashMap<>();
                                int lineCount = ImportWizard.readBook(moved,wizardFields,false);
                                wizardInfo = new  WizardInfo(fileName,null);
                                wizardInfo.setFields(wizardFields);
                                wizardInfo.setLineCount(lineCount);
                                loggedInUser.setWizardInfo(wizardInfo);

                            }
                            else {
                                String data = new String(Files.readAllBytes(Paths.get(moved.getAbsolutePath())), Charset.defaultCharset());
                                if (data.charAt(0) == '[') {
                                    data = "{data:" + data + "}";
                                }
                                data = data.replace("\n", "");//remove line feeds
                                wizardInfo = new WizardInfo(moved.getName(), data);
                                loggedInUser.setWizardInfo(wizardInfo);
                            }
                        }else{
                            OPCPackage opcPackage = org.apache.poi.openxml4j.opc.OPCPackage.open(new FileInputStream(new File(moved.getAbsolutePath())));
                            org.apache.poi.ss.usermodel.Workbook book;
                            book = new org.apache.poi.xssf.usermodel.XSSFWorkbook(opcPackage);
                            String error = ImportWizard.reloadWizardInfo(loggedInUser, book);
                            nextStage = 2;
                            for (String field:wizardInfo.getFields().keySet()){
                                 WizardField wizardField = wizardInfo.getFields().get(field);
                                 if (wizardField.getType()!=null && nextStage < 3){
                                     nextStage = 3;
                                }
                                if (wizardField.getParent()!=null && nextStage < 6){
                                    nextStage = 6;
                                }
                                if (wizardField.getChild()!=null && nextStage < 7){
                                    nextStage = 7;
                                }
                                if (wizardField.getAnchor()!=null && nextStage < 8){
                                    nextStage = 8;
                                }
                            }
                            stage = nextStage;
                            model.put("error", error);
                            if (opcPackage != null) opcPackage.revert();
                            importedSavedVersion = true;


                        }
                    }
                    StringBuffer error = new StringBuffer();
                    List<String> peersChosen = new ArrayList<>();
                    if (wizardInfo.getFields()!=null && !importedSavedVersion){
                        Map<String, String> reverseMap = new HashMap<>();
                                 model.put("dataparent", dataParent);
                        if (dataParent!=null && stage!= 5){
                            for (String heading:wizardInfo.getFields().keySet()){
                                WizardField wizardField = wizardInfo.getFields().get(heading);
                                if (dataParent.equals(wizardField.getParent())){
                                    peersChosen = wizardField.getPeers();
                                    break;
                                }
                            }
                        }
                        for (String heading : wizardInfo.getFields().keySet()) {
                            WizardField wizardField = wizardInfo.getFields().get(heading);
                            String suggestedName = request.getParameter("name_" + heading);
                            if (suggestedName != null) {
                                if (reverseMap.get(suggestedName) != null) {
                                    error.append("Duplicate names for " + reverseMap.get(suggestedName) + " and " + heading + "</br>");
                                    fieldSelected = "";
                                }
                                reverseMap.put(suggestedName, heading);
                                wizardField.setName(suggestedName);
                            }
                            if (stage == 2) {
                                String ignore = request.getParameter("ignore_" + heading);
                                if (ignore != null) {
                                    if (wizardField.getAdded()){
                                        wizardInfo.getFields().remove(heading);
                                        nextStage = stage;
                                    }else{
                                        wizardField.setIgnore(true);
                                    }
                                } else {
                                    wizardField.setIgnore(false);
                                }
                            }
                            if (stage == 3) {
                                String type = request.getParameter("type_" + heading);
                                if ("null".equals(type) || "".equals(type)) type=null;
                                 wizardField.setType(type);

                            }
                            if (stage == 4 && newParent != null && newParent.length() > 0) {
                                if (request.getParameter("child_" + heading) != null) {
                                    wizardField.setParent(newParent);
                                    wizardField.setPeers(peersChosen);
                                } else {
                                    if (wizardField.getParent() != null && wizardField.getParent().equalsIgnoreCase(newParent)) {
                                        wizardField.setParent(null);
                                        wizardField.setPeers(null);
                                    }
                                }
                            }
                            if (stage == 5) {
                                if (request.getParameter("peer_" + heading) != null) {
                                    peersChosen.add(heading);
                                }
                            }
                            if (stage == 6) {
                                String child =request.getParameter("child_" + heading);
                                if (child != null && child.length()>0){
                                    wizardField.setChild(child);
                                }else{
                                    wizardField.setChild(null);
                                }
                            }
                            if (stage == 7) {
                                String anchor =request.getParameter("attribute_" + heading);
                                if (anchor != null && anchor.length()>0){
                                    wizardField.setAnchor(anchor);
                                }else{
                                    wizardField.setAnchor(null);
                                }
                            }
                            ImportWizard.setInterpretation(wizardInfo,wizardField);

                        }


                        if (peersChosen.size() > 0) {
                            for (String heading : wizardInfo.getFields().keySet()) {
                                WizardField wizardField = wizardInfo.getFields().get(heading);
                                if (newParent.equals(wizardField.getParent())){
                                    wizardField.setPeers(peersChosen);
                                    ImportWizard.setInterpretation(wizardInfo,wizardField);
                                }
                            }
                        }
                    }
                     if (nextStage==5){
                         boolean hasData = false;
                         for (String heading:wizardInfo.getFields().keySet()){
                             if ("data".equals(wizardInfo.getFields().get(heading).getType())){
                                 hasData = true;
                                 break;
                             }
                         }
                         if (!hasData){
                             model.put("error","You should usually set up some data fields before proceding - press 'last' to do so");
                          }
                     }
                     if (nextStage==6){
                         for (String heading:wizardInfo.getFields().keySet()){
                             if ("data".equals(wizardInfo.getFields().get(heading).getType()) && wizardInfo.getFields().get(heading).getPeers()==null){
                                 model.put("error","You must allocate peers to " + wizardInfo.getFields().get(heading).getParent());
                                 nextStage = 5;
                                 break;
                             }
                         }
                     }


                     Map<String, WizardField> list = ImportWizard.readTheFile(model, wizardInfo, fieldSelected, valueSelected);
                     Map<String, WizardField> shownList = new LinkedHashMap<>();
                     if (nextStage > 2) {//strip out ignore fields
                         for (String heading : list.keySet()) {
                             if (!list.get(heading).getIgnore()) {
                                 shownList.put(heading, list.get(heading));
                             }
                         }
                     } else {
                         shownList = new LinkedHashMap<>(wizardInfo.getFields());
                        shownList.put("new_field",new WizardField("new_field","new field",true));

                     }
                     if (error.length() > 0) {
                         model.put("error", error.toString());
                     }
                     model.put("fields", shownList);
                 } catch (Exception e) {
                     String exceptionError = e.getMessage();
                     if (exceptionError == null) exceptionError = "null pointer exception";
                     e.printStackTrace();
                     model.put("error", exceptionError);
                     nextStage = stage;
                 }
                 if (fieldSelected!=null){
                     model.put("headingChosen",fieldSelected);
                 }
                fillStages(model, nextStage, wizardInfo);
                setStageOptions(model, loggedInUser.getWizardInfo(), nextStage, dataParent);
                return "importwizard";
            }
        }

        private void fillStages(ModelMap model, int stage, WizardInfo wizardInfo) {
            model.put("stage", stage);
            switch (stage) {
                case 1:
                    model.put("stageheading", "1 Load the file");
                    model.put("stageexplanation", "1 Use the button to upload a text or JSON file, and press 'next' at the bottom.");
                    break;
                case 2:
                    model.put("stageheading", "2 Name the fields");
                    model.put("stageexplanation", "Enter understandable names against each field, check the fields you do not need, and press 'next'" +
                            "<br/>NOTE You can also select any value from the dropdown lists, and press 're-show' to see the context for that value.  " +
                            "<br/>To see all fields again, press 're-show' without selecting a value");
                    break;
                case 3:
                    model.put("stageheading", "3 Identify key fields - id and name - and date and time fields");
                    model.put("stageexplanation", "select from the list.  If there is a key field, and the name and id are the same, choose 'key field id'" );
                    break;
                case 4:
                    model.put("stageheading", "4 Identify sets of data fields");
                    model.put("stageexplanation", "Select any set of DATA that you want to treat as a group." +
                            "<br/>In this context, DATA consists of values you would like to plot - e.g sales numbers, instrument readings. ");
                    break;
                case 5:
                    model.put("stageheading", "5 For the data fields you have chosen, select which associated fields are REQUIRED to define the value");
                    model.put("stageexplanation", "Choose only those fields that are necessary." +
                            "<br/> e.g  If an item of data needs a customer Id, then the Id is sufficient - do not choose any other attribute that can be inferred from the customer Id");
                    break;
                case 6:
                    model.put("stageheading", "6 Find the parent/child relationships");
                    model.put("stageexplanation", "typical parent/child relationsips are Customer->order->order item, country->town->street " +
                            "<br/>if any of the remaining fields are parents, please choose the child element");
                    break;
                case 7:
                    model.put("stageheading", "7 Fill in the attributes");
                    model.put("stageexplanation", "attributes are values that you would not usually use as selectors on tables, but might want to see as part of the data " +
                            "<br/>e.g `telephone no` or `address` might be attributes of `customer`");
                    break;
                case 8:
                    int undefinedFieldCount = 0;
                    for (String field : wizardInfo.getFields().keySet())
                        if (!wizardInfo.getFields().get(field).getIgnore() && wizardInfo.getFields().get(field).getInterpretation().length() == 0) {
                            undefinedFieldCount++;
                        }
                    model.put("stageheading", "8 Ready to go?");
                    String progressReport = "Are you ready to create the database and reports?" +
                            "<br/> You can press 'back' to adjust your choices, or press for special case conditions`";
                    if (undefinedFieldCount > 0) {
                        progressReport = "You have " + undefinedFieldCount + " fields that you have not specified<br/>" + progressReport;
                    }
                    model.put("stageexplanation", progressReport);


            }
        }



        private void setStageOptions(ModelMap model, WizardInfo wizardInfo, int stage, String dataParent){
            Map<String, WizardField> shownFields = wizardInfo.getFields();
            if (stage == 2) {
                for (String heading : shownFields.keySet()) {
                    if (wizardInfo.getFields().get(heading).getIgnore()) {
                        model.put("ignore_" + heading, true);

                    }
                }
            }

            if (stage == 3) {
                List<String> options = new ArrayList<>();
                model.put("options", Arrays.asList(ImportWizard.TYPEOPTIONS));
            }
            if (stage == 4 || stage == 5) {
                List<String> existingParents = new ArrayList<>();
                for (String heading : shownFields.keySet()) {
                    String parent = shownFields.get(heading).getParent();
                    if (parent != null && parent.length() > 0 && !existingParents.contains(parent)) {
                        existingParents.add(parent);
                        existingParents.add(parent);
                    }
                    model.put("existingparents", existingParents);
                }

                if (existingParents.size()> 0 && stage == 5 && (dataParent == null || dataParent.length() == 0)) {
                    dataParent = existingParents.get(0);
                }
                if (stage == 5) {
                    model.put("dataparent", dataParent);
                    for (String heading : shownFields.keySet()) {
                        WizardField wizardField = shownFields.get(heading);
                        if (dataParent!=null && dataParent.equals(wizardField.getParent())) {
                             wizardInfo.setLastDataField(heading);
                            Map<String, WizardField> list = ImportWizard.readTheFile(model, wizardInfo, heading, null);//find all relevant fields
                            List<String> potentialPeers = new ArrayList<>();
                            for (String potentialName : list.keySet()) {
                                String type = list.get(potentialName).getType();
                                if (!"data".equals(list.get(potentialName).getType())) {
                                    potentialPeers.add(potentialName);
                                }
                            }
                            model.put("potentialPeers", potentialPeers);
                            List<String> peersChosen = wizardField.getPeers();
                            if (peersChosen != null) {
                                model.put("peersChosen", peersChosen);

                            }
                        }
                    }
                }
            }
            if (stage == 6) {

                Map<String, WizardField> list = ImportWizard.readTheFile(model, wizardInfo, wizardInfo.getLastDataField() ,null);//find all relevant fields
                 Set<String> peers = new HashSet<>();
                Set<String> undefinedFields = new LinkedHashSet<>();
                Set<String> possibleChildFields = new LinkedHashSet<>();
                for (String heading : list.keySet()) {

                    WizardField wizardField = wizardInfo.getFields().get(heading);
                    if (!wizardField.getIgnore()) {
                        if ("data".equals(wizardField.getType())) {
                            peers.addAll(wizardField.getPeers());
                        } else {
                            undefinedFields.add(heading);
                            possibleChildFields.add(heading);
                        }
                    }
                }
                model.put("possibleChildFields", possibleChildFields);
                undefinedFields.removeAll(peers);
                model.put("undefinedFields", undefinedFields);
            }
            if (wizardInfo.getFields() != null) {
                model.put("fieldCount", wizardInfo.getFields().size());
            }

            if (stage == 7) {

                Map<String, WizardField> list = ImportWizard.readTheFile(model, wizardInfo, wizardInfo.getLastDataField() ,null);//find all relevant fields
                Set<String> peers = new HashSet<>();
                Set<String> possibleAnchorFields = new LinkedHashSet<>();
                Set<String> possibleAttributeFields = new LinkedHashSet<>();
                for (String heading : list.keySet()) {

                    WizardField wizardField = wizardInfo.getFields().get(heading);
                    if (!wizardField.getIgnore()) {
                        String interpretation = wizardField.getInterpretation();
                        if (interpretation == null || interpretation.length() == 0 || interpretation.startsWith("attribute of ")) {
                            possibleAttributeFields.add(heading);
                        } else {
                            if (!interpretation.contains("datatype")) {
                                possibleAnchorFields.add(heading);
                            }
                        }
                    }
                }
                model.put("possibleAttributeFields", possibleAttributeFields);
                model.put("possibleAnchorFields", possibleAnchorFields);
            }


        }

    }





