package com.azquo.dataimport;

import com.azquo.DateUtils;
import com.azquo.LineIdentifierLineValue;
import com.azquo.StringLiterals;
import com.azquo.rmi.RMIClient;
import com.azquo.spreadsheet.LoggedInUser;
import com.azquo.spreadsheet.transport.UploadedFile;
import com.azquo.spreadsheet.zk.BookUtils;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvParser;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import javafx.collections.transformation.SortedList;
import jdk.nashorn.internal.ir.RuntimeNode;
import org.apache.commons.lang.math.NumberUtils;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.AreaReference;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.ui.ModelMap;

import javax.servlet.http.HttpServletRequest;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public class ImportWizard {




    static class JSONtest {
        String target;
        String source;
        String jsonField;
        String jsonPartner;
        String jsonValue;
        String testValue;

        JSONtest(String field, String jsonField, String jsonPartner, String jsonValue, String testValue) {
            this.target = field;
            int lastFieldPos = jsonField.lastIndexOf(ImportService.JSONFIELDDIVIDER);
            this.source = ImportService.JSONFIELDDIVIDER + jsonField.substring(0,lastFieldPos);
            this.jsonField = jsonField.substring(lastFieldPos + 1);
            this.jsonPartner = jsonPartner;
            this.jsonValue = jsonValue;
            this.testValue = testValue;
        }

    }

    static Workbook wb = new XSSFWorkbook();
    // fragment to add our function(s) -  NEEDS WORK!
    //String[] functionNames = {"standardise"};
    //FreeRefFunction[] functionImpls = {new POIStandardise()};
    //UDFFinder udfs = new DefaultUDFFinder(functionNames, functionImpls);
    //UDFFinder udfToolpack = new AggregatingUDFFinder(udfs);

    //wb.addToolPack(udfToolpack);
    static Cell excelCell = wb.createSheet("new sheet").createRow(0).createCell(0);
    static final FormulaEvaluator formulaEvaluator = wb.getCreationHelper().createFormulaEvaluator();


    public static final String[] TYPEOPTIONS = {"key field id","key field name", "date",  "us date", "time","datetime"};

    private static int nthLastIndexOf(int nth, String ch, String string) {
        if (nth <= 0) return string.length();
        if (string.lastIndexOf(ch)==-1) return -1;//start of string
        return nthLastIndexOf(--nth, ch, string.substring(0, string.lastIndexOf(ch)));

    }

    public static Map<String,WizardField> readTheFile(ModelMap model, WizardInfo wizardInfo, String testField, String testItem) {
        try{

            if (wizardInfo.getImportFileData()!=null){
                Map<String,WizardField> list = readTheFile(wizardInfo,testField,testItem);
                if (testItem==null){
                    for (String field:wizardInfo.getFields().keySet()){
                        WizardField wizardField = wizardInfo.getFields().get(field);
                        if (wizardField.getAdded()){
                            list.put(field,wizardField);
                        }
                    }
                }
                return list;

            }else{
                Map<String,WizardField> list = new LinkedHashMap<>();
                boolean found = false;
                for (String field:wizardInfo.getFields().keySet()){
                    WizardField wizardField = wizardInfo.getFields().get(field);
                    if (!wizardField.getIgnore()){
                        list.put(field,wizardField);
                    }
                }
                if (testField!=null && testField.length() > 0){
                    WizardField wizardField = wizardInfo.getFields().get(testField);
                    for (int i=0;i<wizardInfo.getLineCount();i++){
                        if (wizardField.getValuesFound().get(i).equals(testItem)){
                            for (String field:wizardInfo.getFields().keySet()){
                                WizardField wizardField1 = wizardInfo.getFields().get(field);
                                wizardField1.setValueFound(wizardField1.getValuesFound().get(i));

                            }
                            break;
                        }
                    }
                }
                return list;
            }
        }catch (Exception e){
            model.put("error", e.getMessage());
            return new LinkedHashMap<>();
        }
    }




    public static Map<String,WizardField> readTheFile(WizardInfo wizardInfo, String testField, String testItem)throws Exception  {
        Map<String,List<String>> fieldsFound = new LinkedHashMap<>();
        List<JSONtest> jsonTests = new ArrayList<>();
        String error = createJSONTests(wizardInfo, jsonTests, testField, testItem);
        if (error!=null){
            throw new Exception(error);
        }
        if (testItem==null){
            testItem = "*";
        }
        if (testField!=null && testField.length()> 0 && !wizardInfo.getFields().get(testField).getAdded()){
            testItem = ImportService.JSONFIELDDIVIDER + testField + ImportService.JSONFIELDDIVIDER + testItem;
        }else{
            testItem = "";
        }

        try {
            JSONObject jsonObject = wizardInfo.getImportFileData();//remove line feeds
            traverseJSON(fieldsFound, "", jsonObject,10, testItem, jsonTests);
            if (wizardInfo.getFields().size() == 0) {
                Map<String ,String> reverseNames = new HashMap<>();
                for (String field : fieldsFound.keySet()) {

                    String suggestedName = field;
                    try {
                        suggestedName = humanifyName(field.substring(field.lastIndexOf(ImportService.JSONFIELDDIVIDER) + 1));
                        if (reverseNames.get(suggestedName) != null) { //if suggested names may be duplicates, grab a bit more string
                            String original = reverseNames.get(suggestedName);
                            String originalSuggestion = original.substring(nthLastIndexOf(2,  ImportService.JSONFIELDDIVIDER, original) + 1);
                            originalSuggestion = humanifyName(originalSuggestion);
                            reverseNames.remove(suggestedName);
                            reverseNames.put(originalSuggestion, original);
                            wizardInfo.getFields().get(original).setName(originalSuggestion);
                            suggestedName = field.substring(nthLastIndexOf(2, ImportService.JSONFIELDDIVIDER, field) + 1);
                        }
                    }catch(Exception e){
                     }
                    reverseNames.put(suggestedName,field);
                    WizardField wizardField = new WizardField(field,insertSpaces(suggestedName),false);
                    wizardField.setValuesFound(fieldsFound.get(field));
                    setDistinctCount(wizardField);
                    //adjustValuesFound(wizardField,fieldsFound.get(field));
                    wizardInfo.getFields().put(field,wizardField);
                }
            }else{
                if (testItem!=null) {
                    for (String field : fieldsFound.keySet()) {
                        if (fieldsFound.get(field).size() > 0){
                            wizardInfo.getFields().get(field).setValueFound(fieldsFound.get(field).get(0));
                        }else{
                            wizardInfo.getFields().get(field).setValueFound("");
                        }
                        //adjustValuesFound(wizardInfo.getFields().get(field),fieldsFound.get(field));
                    }
                }
            }
            Map<String,WizardField> toReturn = new LinkedHashMap<>();
            for (String field:fieldsFound.keySet()){
                toReturn.put(field,wizardInfo.getFields().get(field));
            }
           return toReturn;


        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private static void setDistinctCount(WizardField wizardField){
        Set<String> distinct = new HashSet<>();
        for (String value:wizardField.getValuesFound()){
            distinct.add(value);
        }
        wizardField.setDistinctCount(distinct.size());
    }

    private static void adjustValuesFound(WizardField wizardField, List<String>values){

        if ("date".equals(wizardField.getType()) ){
            wizardField.setValuesFound(adjustDates(values));
        }else{
            if ("time".equals(wizardField.getType())){
                wizardField.setValuesFound(adjustTImes(values));
            }else{
                wizardField.setValuesFound(values);
            }
        }
        setDistinctCount(wizardField);
    }

    private static List<String> adjustDates(List<String> values) {
        List<String >toReturn = new ArrayList<>();
        for (String value : values) {
            if (value.length() > 10) {
                value = value.substring(0, 10);
            }
            try {
                DateUtils.isADate(value);
                toReturn.add(value);
            } catch (Exception e) {
                toReturn.add("invalid date: " + value);
            }
        }
        return toReturn;
    }


    private static List<String> adjustTImes(List<String> values) {
        List<String> toReturn = new ArrayList<>();
        for (String value : values) {
            if (value.length() < 5) {
                try {
                    int time = NumberUtils.toInt(value);
                    int hours = time / 60;
                    int mins = time - 60 * hours;
                    toReturn.add(pad(hours, 2) + ":" + pad(mins, 2));
                } catch (Exception e) {
                    toReturn.add(value);
                }
            } else {
                toReturn.add(value);
            }
        }
        return toReturn;
    }


    private static String pad(int val, int len) {
        String toReturn = val + "";
        if (toReturn.length()<len){
            return "000000".substring(0,len-toReturn.length()) + toReturn;
        }
        return toReturn;


    }


    private static String humanifyName(String name){
        name = name.replace("_"," ").replace(ImportService.JSONFIELDDIVIDER + ""," ");
        name = name.substring(0,1).toUpperCase() + name.substring(1);
        return name;

    }

    private static boolean traverseJSON(Map<String,List<String>>fieldsFound, String jsonPath,JSONObject jsonNext, int maxArraySize, String testItem, List<JSONtest> jsonTests) throws Exception{
        boolean found = false;
        String[] jsonNames = JSONObject.getNames(jsonNext);
        boolean tested = false;
        for(JSONtest jsoNtest:jsonTests){
            if (jsoNtest.source.equals(jsonPath)){
                try{
                    tested = true;
                    String jsonOtherValue = jsonNext.getString(jsoNtest.jsonPartner);
                    if (jsonOtherValue.equals(jsoNtest.jsonValue)){
                        String jsonValue = jsonNext.getString(jsoNtest.jsonField);
                        if (jsoNtest.testValue==null || jsoNtest.testValue.equals(jsonValue)) {
                            try {
                                fieldsFound.get(jsoNtest.target).add(jsonValue);
                            } catch (Exception e) {
                                fieldsFound.put(jsoNtest.target, new ArrayList<>());
                                fieldsFound.get(jsoNtest.target).add(jsonValue);
                            }
                            if (jsoNtest.testValue!=null){
                                found = true;
                            }
                        }
                    }
                }catch(Exception e){
                    //should we return an error?
                }
            }
        }
        if (tested) return found;
        for (String jsonName:jsonNames){
            String newPath = jsonPath + ImportService.JSONFIELDDIVIDER + jsonName;
            if (testItem.length()>0 && fieldsFound.get(newPath.substring(1))!=null){
                fieldsFound.get(newPath.substring(1)).clear();
            }
            if(testItem.length()==0 || testItem.startsWith(jsonPath)) {
                try {
                    JSONObject jsonObject = jsonNext.getJSONObject(jsonName);
                    if(traverseJSON(fieldsFound, newPath, jsonObject, maxArraySize, testItem, jsonTests)){
                        found = true;
                    };
                } catch (Exception e) {
                    try {

                        JSONArray jsonArray = jsonNext.getJSONArray(jsonName);
                        int count = 0;
                        for (Object jsonObject1 : jsonArray) {
                            if (found) break;
                            if(traverseJSON(fieldsFound, newPath, (JSONObject) jsonObject1, maxArraySize, testItem, jsonTests)){
                                found = true;
                            };
                            if (count++ == maxArraySize) {
                                break;
                            }

                        }
                    } catch (Exception e2) {

                        String value = jsonNext.get(jsonName).toString().trim();
                        if (value.length()>0 && (testItem.equals(newPath+ImportService.JSONFIELDDIVIDER + value) || testItem.equals(newPath+ImportService.JSONFIELDDIVIDER + "*"))){

                            found = true;
                        }
                        if (value.length()>0) {
                            List<String> valuesFound = fieldsFound.get(newPath.substring(1));
                            if (valuesFound == null) {
                                valuesFound = new ArrayList<>();
                                fieldsFound.put(newPath.substring(1), valuesFound);
                            }
                             fieldsFound.get(newPath.substring(1)).add(value);
                        }
                        //set the value
                    }
                }
            }
        }
        return found;
    }

    public static String reloadWizardInfo(LoggedInUser loggedInUser, org.apache.poi.ss.usermodel.Workbook book){
        try {
            org.apache.poi.ss.usermodel.Sheet importSheet = book.getSheetAt(0);
            WizardInfo wizardInfo = loggedInUser.getWizardInfo();
            org.apache.poi.ss.usermodel.Name headingRegion = BookUtils.getName(book, "az_Headings");
            AreaReference areaReference = new AreaReference(headingRegion.getRefersToFormula(),null);

            int row = areaReference.getFirstCell().getRow() + 1;
            int col = areaReference.getFirstCell().getCol();
            int startRow = row;
            //remove the added fields...
            List<String> addedFields = new ArrayList<>();
            for (String field : wizardInfo.getFields().keySet()) {
                if (wizardInfo.getFields().get(field).getAdded()) {
                    addedFields.add(field);
                }
                wizardInfo.getFields().get(field).setIgnore(true);
            }
            for (String field:addedFields){
                wizardInfo.getFields().remove(field);
            }

                //import the names first - peers are described by the chosen names rather than the import file names.

            while(ImportService.getCellValue(importSheet,row,col).length()>0){
               String loadedField = ImportService.getCellValue(importSheet, row, col);
                String azquoName = ImportService.getCellValue(importSheet,row,col + 1);
                WizardField wizardField = wizardInfo.getFields().get(HTMLify(loadedField));
               if (wizardField==null){
                   wizardField = new WizardField(loadedField,insertSpaces(azquoName),true);
                   wizardInfo.getFields().put(HTMLify(loadedField),wizardField);
                }else{
                   wizardField.setName(azquoName);
               }
               wizardField.setIgnore(false);
               row++;

           }
            row = startRow;
            while(ImportService.getCellValue(importSheet,row,col).length()>0){
                String loadedField = ImportService.getCellValue(importSheet, row, col);
                WizardField wizardField = wizardInfo.getFields().get(HTMLify(loadedField));
                String interpetation = ImportService.getCellValue(importSheet,row,col + 3);
                String[] clauses = interpetation.split(";");
                if (Arrays.asList(TYPEOPTIONS).contains(clauses[0])){
                    wizardField.setType(clauses[0]);

                }
                String dataChild = getClause("parent of", clauses);
                if (dataChild!=null){
                    wizardField.setChild(findFieldFromName(wizardInfo,dataChild));
                }
                String dataAnchor = getClause("attribute of", clauses);
                if (dataAnchor!=null){
                    wizardField.setAnchor(findFieldFromName(wizardInfo,dataAnchor));
                }
                String dataParent = getClause("datagroup", clauses);
                wizardField.setParent(dataParent);
                String peersString = getClause("peers", clauses);
                if (peersString!=null){
                    setPeers(wizardInfo, wizardField, peersString);
                 }
                setInterpretation(wizardInfo,wizardField);
                row++;
                // todo don't show admins if not admin
            }
            //repass interpretations to get all 'peer for' instances
            for (String field:wizardInfo.getFields().keySet()){
                setInterpretation(wizardInfo,wizardInfo.getFields().get(field));
            }

            calcXL(loggedInUser.getWizardInfo());
            return "";
        }catch(Exception e){
            return e.getMessage();
        }


    }

    private static void setPeers(WizardInfo wizardInfo, WizardField wizardField, String peersString){
        peersString = peersString.trim();
        peersString = peersString.substring(1,peersString.length()-1);//strip{}
        String[]peers = peersString.split(",");
        List<String> dataPeers = new ArrayList<>();
        for (String peer:peers){
            String dataField = findFieldFromName(wizardInfo,peer);
            if (dataField != null){
                dataPeers.add(dataField);

            }
        }
        if (dataPeers.size()>0){
            wizardField.setPeers(dataPeers);
        }else{
            wizardField.setPeers(null);
        }

    }

    private static String getClause(String toFind, String[] array){
        for(String element:array){
            if (element.startsWith(toFind)){
                return element.substring(toFind.length()).trim();
            }
        }
        return null;
    }

    public static String processFound(HttpServletRequest request, WizardInfo wizardInfo, int stage){
        String dataParent = request.getParameter("existingparent");
        String newParent = request.getParameter("newparent");
        if (dataParent==null || dataParent.length()==0){
            dataParent = newParent;
        }
        List<String> peersChosen = new ArrayList<>();
        if (dataParent!=null && stage!= 5){
            for (String heading:wizardInfo.getFields().keySet()){
                WizardField wizardField = wizardInfo.getFields().get(heading);
                if (dataParent.equals(wizardField.getParent())){
                    peersChosen = wizardField.getPeers();
                    break;
                }
            }
        }
        Map<String, String> reverseMap = new HashMap<>();
        for (String heading : wizardInfo.getFields().keySet()) {
            WizardField wizardField = wizardInfo.getFields().get(heading);
            String suggestedName = request.getParameter("name_" + heading);
            String newValue = findAction(wizardInfo,heading, "name");
            if (newValue!=null){
                suggestedName = newValue;
            }
            if (suggestedName != null) {
                if (reverseMap.get(suggestedName) != null) {
                    return  "Duplicate names for " + reverseMap.get(suggestedName) + " and " + heading + "</br>";
                }
                reverseMap.put(suggestedName, heading);
                wizardField.setName(suggestedName);
            }
            if (stage == 2 && findAction(wizardInfo,heading,"ignore")==null) {
                String ignore = request.getParameter("ignore_" + heading);
                if (ignore != null) {
                    if (wizardField.getAdded()){
                        wizardInfo.getFields().remove(heading);
                    }else{
                        wizardField.setIgnore(true);
                    }
                } else {
                    wizardField.setIgnore(false);
                }
            }
            if (stage == 3) {
                if (findAction(wizardInfo,heading,"type")==null) {
                    String type = request.getParameter("type_" + heading);
                    if ("null".equals(type) || "".equals(type)) type = null;
                    wizardField.setType(type);
                }

            }
            if (stage == 4 && newParent != null && newParent.length() > 0 && findAction(wizardInfo,heading,"child")==null) {

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
            if (stage == 5&& findAction(wizardInfo,heading,"peers")==null) {
                if (request.getParameter("peer_" + heading) != null) {
                    peersChosen.add(heading);
                }
            }
            if (stage == 6&& findAction(wizardInfo,heading,"parent")==null) {
                String child =request.getParameter("child_" + heading);
                if (child != null && child.length()>0){
                    wizardField.setChild(child);
                }else{
                    wizardField.setChild(null);
                }
            }
            if (stage == 7&& findAction(wizardInfo,heading,"anchor")==null) {
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
        return "";


    }

    private static String findAction(WizardInfo wizardInfo, String field, String action){
        for (String actionString:wizardInfo.getSuggestionActions()){
            String[]elements = actionString.split(";");
            if (field.equals(elements[0])&& action.equals(elements[1])){
                return elements[2];
            }
        }
        return null;
    }

    public static void setInterpretation(WizardInfo wizardInfo, WizardField wizardField){

        String field = findFieldFromName(wizardInfo,wizardField.getName());
        List<String> peerHeadings = new ArrayList<>();
        for (String heading:wizardInfo.getFields().keySet()){
            WizardField peerField = wizardInfo.getFields().get(heading);
            if (peerField.getPeers()!=null && peerField.getPeers().contains(field)){
                if (!peerHeadings.contains(peerField.getParent())){
                    peerHeadings.add(peerField.getParent());
                }
            }
        }


        StringBuffer toReturn = new StringBuffer();

        String type = wizardField.getType();
        if (type!=null){
            toReturn.append(";" + type);
        }
        String peerType = null;
        if (peerHeadings.size() > 0){
            peerType ="peer for " + peerHeadings.get(0);
            for (int peer = 1;peer<peerHeadings.size();peer++){
                peerType +="," +peerHeadings.get(peer);
            }
           toReturn.append(";" + peerType);
        }

        if (wizardField.getChild()!=null){
             toReturn.append(";parent of " + wizardInfo.getFields().get(wizardField.getChild()).getName());
        }
        if (wizardField.getAnchor()!=null && !"key field name".equals(wizardField.getType())){
            toReturn.append(";attribute of " + wizardInfo.getFields().get(wizardField.getAnchor()).getName());
        }

        if (wizardField.getParent()!=null){
            toReturn.append(";datagroup " + wizardField.getParent());
        }

        if ("date".equals(wizardField.getType())){
            toReturn.append(";datatype date");
        }



        if (toReturn.length()==0) {

            wizardField.setInterpretation(checkForParents(wizardInfo, wizardField));

            return;
        }

        if (wizardField.getPeers()!=null && wizardField.getPeers().size()>0){
            //this is rather a tiresome routine to mark the peers with 'peer for <data parent name>' - we don't need this info in Azquo, but it's helpful to the user
            toReturn.append(";peers {");
            boolean firstPeer = true;

            for (String peer:wizardField.getPeers()){
                WizardField wizardPeer = wizardInfo.getFields().get(peer);
                     if (!firstPeer) toReturn.append(",");
                toReturn.append(wizardInfo.getFields().get(peer).getName());
                firstPeer = false;
            }
            toReturn.append("}");
        }
        wizardField.setInterpretation(toReturn.toString().substring(1));

    }

    private static String checkForParents(WizardInfo wizardInfo, WizardField wizardField) {
        for (String possibleParent : wizardInfo.getFields().keySet()) {
            if (wizardField.getName().equals(wizardInfo.getFields().get(possibleParent).getChild())) {
                return "Key field";
            }
        }
        return "";
    }

    private static String findFieldFromName(WizardInfo wizardInfo, String fieldName){
        for (String field:wizardInfo.getFields().keySet()){
            if (wizardInfo.getFields().get(field).getName().equalsIgnoreCase(fieldName.trim())){
                return field;
            }
        }
        return null;
    }

    public static String HTMLify(String string){
        return string.replace(" ","_").replace("\"","").replace("'","").replace("`","");
    }


    public static void createDB(LoggedInUser loggedInUser) throws Exception{
        WizardInfo wizardInfo = loggedInUser.getWizardInfo();
        String dataField = null;
        Set<String> pathsRequired = new HashSet<>();
        Map<String,List<String>> flatfileData = new LinkedHashMap<>();
        Map<String,List<String>> onePassValues = new HashMap<>();
        boolean isJSON = true;
        for (String field:wizardInfo.getFields().keySet()){
            WizardField wizardField = wizardInfo.getFields().get(field);
            if (!wizardField.getIgnore() && wizardField.getInterpretation().length() > 0) {
               if (!field.contains(" where ")){
                   pathsRequired.add(ImportService.JSONFIELDDIVIDER + field);
               }
               if (wizardInfo.getImportFileData()==null){
                    flatfileData.put(field,wizardField.getValuesFound());
                    isJSON = false;
               }else {
                    flatfileData.put(field, new ArrayList<>());
                    onePassValues.put(field, new ArrayList<>());
               }
            }
        }
        if (isJSON) {
            List<JSONtest> jsonTests = new ArrayList<>();
            String error = createJSONTests(wizardInfo, jsonTests, null, null);
            for (JSONtest jsoNtest : jsonTests) {
                pathsRequired.add(jsoNtest.source);
            }
            try {

                traverseJSON3(flatfileData, pathsRequired, onePassValues, "", wizardInfo.getImportFileData(), jsonTests, null);
            } catch (Exception e) {
                throw e;
            }
        }
        String idField = null;
        for (String field:wizardInfo.getFields().keySet()){

            WizardField wizardField = wizardInfo.getFields().get(field);
            if ("key field id".equals(wizardField.getType())){
                idField = field;
            }

            if ("key field name".equals(wizardField.getType())){
                wizardField.setAnchor(idField);
            }
            if ("date".equals(wizardField.getType())){
                flatfileData.put(field,adjustDates(flatfileData.get(field)));
            }
            if ("time".equals(wizardField.getType())){
                flatfileData.put(field,adjustTImes(flatfileData.get(field)));
            }
        }
        /*
        START HERE
        the field names are in wizardInfo - Azquo names as '.name'
        The value array is 'flatfiledata'
         Use the 'interpretation'.
           NEW CLAUSES:
         'date' 'time' 'datetime'  - the type of the data - I'll put it into standard format before it reaches you.
         'datagroup <DT>'    equivalend to 'child of <DT>, where <DT> is also child of 'Data'
         'peer for' and 'parent of'   also include 'child of <field.name>.  May also be worth adding 'language <field.name>'
         Dates.   check max and min, and create a setup sheet for Years, Months, Days to cover the required period
         Needed:
         A database and one or more reports.
*/
        uploadData(loggedInUser,flatfileData);

    }


    private static void uploadData(LoggedInUser loggedInUser, Map<String,List<String>> data)throws Exception{


        Map<String,String> wizardDefs = new HashMap<>();
        WizardInfo wizardInfo = loggedInUser.getWizardInfo();
        for (String field:wizardInfo.getFields().keySet()){
            if (data.get(field)!=null) {

                WizardField wizardField = wizardInfo.getFields().get(field);
                if (wizardField.getInterpretation() != null) {
                    String def = wizardField.getName() + ";" + wizardField.getInterpretation();
                    wizardDefs.put(field, def);
                }
            }
        }

        RMIClient.getServerInterface(loggedInUser.getDatabaseServer().getIp()).uploadWizardData(loggedInUser.getDataAccessToken(),wizardDefs , data);



    }



    private static boolean traverseJSON3(Map<String,List<String>> output, Set<String> pathsRequired, Map<String,List<String>> onePassValues,String jsonPath,JSONObject jsonNext,List<JSONtest>jsonTests, String extractLevel) throws Exception{


        boolean tested = false;
        for(JSONtest jsoNtest:jsonTests){
            if (jsoNtest.source.equals(jsonPath)){
                try{
                    tested = true;
                    String jsonOtherValue = jsonNext.getString(jsoNtest.jsonPartner);
                    if (jsonOtherValue.equals(jsoNtest.jsonValue)){
                        String jsonValue = jsonNext.getString(jsoNtest.jsonField);
                        if (jsoNtest.testValue==null || jsoNtest.testValue.equals(jsonValue)) {
                             onePassValues.get(jsoNtest.target).add(jsonValue);
                        }
                    }
                }catch(Exception e){
                    //should we return an error?
                }
            }
        }
        if (tested) return true;

        String[] jsonNames = JSONObject.getNames(jsonNext);
        for (String jsonName:jsonNames){
            String newPath = jsonPath + ImportService.JSONFIELDDIVIDER + jsonName;
            if(inRequired(newPath, pathsRequired)) {
                try {
                    JSONObject jsonObject = jsonNext.getJSONObject(jsonName);
                    traverseJSON3(output, pathsRequired, onePassValues,newPath, jsonObject, jsonTests, extractLevel);
                } catch (Exception e) {
                    try {

                        JSONArray jsonArray = jsonNext.getJSONArray(jsonName);
                    } catch (Exception e2) {
                        String value = jsonNext.get(jsonName).toString();
                        if (onePassValues.get(newPath.substring(1))!=null){
                            onePassValues.get(newPath.substring(1)).add(value);
                        }
                    }
                }
            }
        }
        for (String jsonName:jsonNames){
            String newPath = jsonPath + ImportService.JSONFIELDDIVIDER + jsonName;
            if(inRequired(newPath, pathsRequired)) {
                try {
                    JSONObject jsonObject = jsonNext.getJSONObject(jsonName);
                } catch (Exception e) {
                    try {

                        JSONArray jsonArray = jsonNext.getJSONArray(jsonName);
                        int count = 0;
                        if (extractLevel==null){
                            extractLevel = newPath;
                        }
                        for (Object jsonObject1 : jsonArray) {
                            traverseJSON3(output, pathsRequired, onePassValues,newPath, (JSONObject) jsonObject1, jsonTests,extractLevel    );
                            if (extractLevel.equals(newPath)){
                                outputAdd(output,onePassValues, newPath.substring(1));
                            }
                        }
                     } catch (Exception e2) {
                    }
                }
            }
        }
        return true;
    }


    private static boolean inRequired(String toTest, Set<String> required){
        for (String maybe:required){
            if (maybe.startsWith(toTest)){
                return true;
            }
        }
        return false;
    }

    private static void outputAdd(Map<String,List<String>> output, Map<String,List<String>> found, String extractLevel){
        int maxCount = 0;
        for (String field:found.keySet()){
            int count = found.get(field).size();
            if (count > maxCount){
                maxCount = count;
            }
        }
        for (String field:output.keySet()){
            output.get(field).addAll(found.get(field));
            if (found.get(field).size() < maxCount){
                String filler = "";
                if (found.get(field).size()>0){
                    filler = found.get(field).get(0);
                }
                for (int i = found.get(field).size();i < maxCount;i++){
                    output.get(field).add(filler);
                }
            }
            if (field.startsWith(extractLevel)){
                found.get(field).clear();
            }
         }
    }

    public static String createJSONTests(WizardInfo wizardInfo, List<JSONtest> jsoNtests, String testField, String testValue)throws Exception{
        String error = null;
        for (String field:wizardInfo.getFields().keySet()){
            WizardField wizardField = wizardInfo.getFields().get(field);
            if (wizardField.getAdded() && wizardField.getImportedName().toLowerCase(Locale.ROOT).contains(" where ")){
                String jsonRule = wizardField.getImportedName();
                String errorFound = "Json rule should be of the form `<jsonpath>` where `peertag` = \"<jsonvalue>\" (e.g)  'data|value' where 'name'=\"contact_name\": " + jsonRule;
                String fieldName = getField(jsonRule,'`');
                if (fieldName==null){
                      error = errorFound;
                      break;
                }
                int divPos = fieldName.lastIndexOf(String.valueOf(ImportService.JSONFIELDDIVIDER));
                if (divPos < 0){
                    error = errorFound;
                    break;
                }
                jsonRule = jsonRule.substring(fieldName.length() + 2).trim();
                if (!jsonRule.toLowerCase(Locale.ROOT).startsWith("where ")){
                    error = errorFound;
                    break;

                }
                jsonRule = jsonRule.substring(6).trim();
                String jsonField = getField(jsonRule,'`');
                if (jsonField == null){
                    error = errorFound;
                    break;
                }
                jsonRule = jsonRule.substring(jsonField.length() + 2).trim();
                if (jsonRule.charAt(0)!='='){
                    error = errorFound;
                    break;
                }
                jsonRule = jsonRule.substring(1).trim();
                String jsonValue = getField(jsonRule,'"');
                if (jsonValue==null){
                    error = errorFound;
                    break;
                }
                String valueToTest = null;
                if (field.equals(testField)){
                    valueToTest = testValue;
                }
                jsoNtests.add(new JSONtest(field, fieldName, jsonField, jsonValue, valueToTest));
            }
        }

        return error;
    }

    private static  String getField(String from, char quote){
        if (from.length()< 2 || from.charAt(0)!=quote || from.indexOf(String.valueOf(quote),1)< 0){
            return null;
        }
        return from.substring(1,from.indexOf(String.valueOf(quote),1));

    }



    public static int readBook(File uploadedFile, Map<String,WizardField> wizardFields, boolean usDates) throws Exception{
        OPCPackage opcPackage;
        try (FileInputStream fi = new FileInputStream(uploadedFile)) { // this will hopefully guarantee that the file handler is released under windows
            opcPackage = OPCPackage.open(fi);
        } catch (Exception e) {
            e.printStackTrace();
            throw new IOException("Cannot load file");
        }
        org.apache.poi.ss.usermodel.Workbook book = new XSSFWorkbook(opcPackage);

        org.apache.poi.ss.usermodel.Sheet sheet = book.getSheetAt(0);
        Map<Integer,String> colMap = new HashMap<>();
        int lineCount = -1;
        int colCount = 0;
        try {
            boolean firstRow = true;
            for (org.apache.poi.ss.usermodel.Row row : sheet) {
                int col = 0;
                for (Iterator<org.apache.poi.ss.usermodel.Cell> ri = row.cellIterator(); ri.hasNext(); ) {
                    org.apache.poi.ss.usermodel.Cell cell = ri.next();
                    while (cell.getColumnIndex() > col){
                        wizardFields.get(colMap.get(col++)).getValuesFound().add("");
                    }
                    String value = ImportService.getCellValueUS(cell, usDates);
                    if (value==null) value = "";
                    if (firstRow){
                        colMap.put(cell.getColumnIndex(),value);
                        WizardField wizardField = new WizardField(value,insertSpaces(value),false);
                        wizardField.setValuesFound(new ArrayList<>());
                        wizardFields.put(value,wizardField);
                        colCount = wizardFields.size();

                    }else{
                        wizardFields.get(colMap.get(cell.getColumnIndex())).getValuesFound().add(value);
                    }
                    col++;
                }
                while (col < colCount){
                    wizardFields.get(colMap.get(col++)).getValuesFound().add("");
                }

                firstRow = false;
                lineCount++;
            }
        }catch (Exception e){
            throw e;
        }
        for (String field:wizardFields.keySet()){
            setDistinctCount(wizardFields.get(field));
        }
        return lineCount;
    }


    private static String insertSpaces(String field){
        int i=1;
        boolean lastCap = true;
        while (i < field.length()){
            char c = field.charAt(i);
            if (c == ' '){
                return field;
            }
            if (c >='A' && c <= 'Z') {
                if(!lastCap) {

                    field = field.substring(0, i) + " " + field.substring(i);

                    i++;
                }
                lastCap = true;
            }else{
                lastCap = false;
            }
            i++;
        }
        return field;

    }

    public static void calcXL(WizardInfo wizardInfo)throws Exception{

        for (String maybeXL:wizardInfo.getFields().keySet()){
           WizardField xlField = wizardInfo.getFields().get(maybeXL);
             if (xlField.getAdded()){

                Map<String,String> fieldList = new HashMap<>();
                for (String field:wizardInfo.getFields().keySet()){
                    WizardField wizardField = wizardInfo.getFields().get(field);
                    if (xlField.getImportedName().contains(StringLiterals.QUOTE + wizardField.getName() + StringLiterals.QUOTE)){
                        fieldList.put(StringLiterals.QUOTE + wizardField.getName() + StringLiterals.QUOTE, field);
                    }
                }
                xlField.setValuesFound(new ArrayList<>());
                for(int i=0;i <wizardInfo.getLineCount();i++){
                    String compositionPattern = xlField.getImportedName();
                    for (String field:fieldList.keySet()) {
                        compositionPattern = compositionPattern.replace(field, getXLTerm(wizardInfo,fieldList.get(field),i));
                    }
                    excelCell.setCellFormula(compositionPattern);
                    formulaEvaluator.clearAllCachedResultValues();
                    compositionPattern = formulaEvaluator.evaluate(excelCell).formatAsString();
                    if (compositionPattern.startsWith("\"") && compositionPattern.endsWith("\"")) {
                        compositionPattern = compositionPattern.substring(1, compositionPattern.length() - 1);
                    }
                    // for Excel date is a number - on the way out standardise to our typically used date format
                    if (HeadingReader.DATELANG.equals(xlField.getType()) || HeadingReader.USDATELANG.equals(xlField.getType())){
                        try{
                            compositionPattern = DateUtils.toDate(compositionPattern);
                        } catch (Exception e){
                            throw new Exception("Cannot read : " + compositionPattern + " as date. Try surrounding with DATEVALUE()?");
                        }
                    }
                    xlField.getValuesFound().add(compositionPattern);
                }
                setDistinctCount(xlField);
            }
        }

    }

    public static String getXLTerm(WizardInfo wizardInfo, String field, int i){
        String sourceVal =  wizardInfo.getFields().get(field).getValuesFound().get(i);
        if (NumberUtils.isNumber(sourceVal)){
            return sourceVal;
        }
        return "\"" + sourceVal + "\"";

    }

    public static void acceptSuggestions(WizardInfo wizardInfo){
        for (String action:wizardInfo.getSuggestionActions()){
            String[]elements = action.split(";");
            WizardField wizardField = wizardInfo.getFields().get(elements[0]);
            if (wizardField==null){
                //add a new field
                wizardField = new WizardField("1", elements[0], true);
                wizardInfo.getFields().put("1",wizardField);
                try{
                    calcXL(wizardInfo);
                }catch (Exception e){

                }
            }

            if (elements[1].equals("name")){
                wizardField.setName(elements[2]);
            }
            if (elements[1].equals("type")){
                wizardField.setType(elements[2]);
            }


            if (elements[1].equals("parent")){
                wizardField.setParent(elements[2]);
                if (wizardField.getPeers()==null){
                    wizardField.setPeers(new ArrayList<>());
                }
            }
            if (elements[1].equals("child")){
                wizardField.setChild(elements[2]);
            }
            if (elements[1].equals("anchor")){
                wizardField.setAnchor(elements[2]);
            }
            if (elements[1].equals("peers")){
                setPeers(wizardInfo,wizardField,elements[2]);

            }
        }

    }

    public static void makeSuggestion(ModelMap model, WizardInfo wizardInfo, int stage) {
        model.put("suggestion", "no suggestion");
        wizardInfo.setLastDataField(null);
        wizardInfo.setSuggestionActions(new ArrayList<>());
        if (!wizardInfo.hasSuggestions){
            return;
        }
        StringBuffer suggestion = new StringBuffer();
        List<String> suggestionActions= new ArrayList<>();
        String suggestionReason="";
        String idField = null;
        String idFieldName = null;
        for (String field:wizardInfo.getFields().keySet()){
            WizardField wizardField = wizardInfo.getFields().get(field);
            if ("key field id".equals(wizardField.getType())){
                idField = field;
                idFieldName = wizardField.getName();
            }
        }

        try {
            switch (stage) {
                case 2:
                    //check names for short names
                    Set<String> shortNames = new HashSet<>();
                    for (String field : wizardInfo.getFields().keySet()) {
                        String name = wizardInfo.getFields().get(field).getName();
                        if (name.length() < 4) {
                            shortNames.add(name);
                        }
                    }
                    if (shortNames.size() > 0) {
                        for (String field : wizardInfo.getFields().keySet()) {
                            WizardField wizardField = wizardInfo.getFields().get(field);

                            Set<String> fieldData = new HashSet<>(wizardField.getValuesFound());
                            fieldData.retainAll(shortNames);
                            if (fieldData.size() > 1) {
                                //this is prbably a lookup for the field names
                                String fullNameField = null;
                                for (String shortName : fieldData) {
                                    Map<String, WizardField> list = readTheFile(model, wizardInfo, field, shortName);
                                    if (list!=null) {
                                        for (String indexField : list.keySet()) {
                                            String val = list.get(indexField).getValueFound();
                                            if (val.length() > 3) {
                                                //probably the field name
                                                fullNameField = indexField;
                                                break;
                                            }
                                        }
                                    }
                                }
                                if (fullNameField!=null) {
                                    suggestion.append("Map the following names: ");
                                    for (String shortName : fieldData) {
                                        Map<String, WizardField> list = readTheFile(model, wizardInfo, field, shortName);
                                        String fullName = list.get(fullNameField).getValueFound();
                                        suggestionActions.add(findFieldFromName(wizardInfo, shortName) + ";name;" + fullName);
                                        suggestion.append(shortName + "->" + fullName + ", ");
                                    }
                                    wizardInfo.setSuggestionActions(suggestionActions);

                                    model.put("suggestionReason", "The field names are appreviations, which all appear in the data for " + field + ".<br/> The suggested names are data from the associated field " + fullNameField);
                                    model.put("suggestion", suggestion.toString().substring(0,suggestion.length()-1));
                                    return;
                                }

                            }
                        }
                    }
                    break;

                case 3:
                    //dates times, key fields
                    suggestionReason = "data can be interpreted as ";
                    idField = wizardInfo.getFields().keySet().iterator().next();

                    if (idField.toLowerCase(Locale.ROOT).endsWith("id")) {
                        WizardField wizardField = wizardInfo.getFields().get(idField);
                        suggestion.append(idField + " is the key field id, ");
                        suggestionActions.add(idField + ";type;key field id");
                        suggestionReason += idField + " is an ID - (name ends with 'id'),";
                    }else{
                        idField = null;
                    }
                    if (idField!=null) {
                        for (String field : wizardInfo.getFields().keySet()) {
                            if (field.toLowerCase(Locale.ROOT).endsWith("name")) {
                                suggestion.append(field + " is the key field name, ");
                                suggestionActions.add(field + ";type;key field name");
                                suggestionReason += field + " is the key field name - (field name ends with 'name'),";
                                break;
                            }
                        }
                    }

                    for (String field:wizardInfo.getFields().keySet()) {
                        WizardField wizardField = wizardInfo.getFields().get(field);
                        int valCOunt = wizardField.getValuesFound().size();
                        if (valCOunt > 100) {
                            valCOunt = 100;

                        }
                        boolean found = true;
                        for (int i = 0; i < valCOunt; i++) {
                            java.time.LocalDate date = DateUtils.isDateTime(wizardField.getValuesFound().get(i));
                            if (date==null){
                                found = false;
                                break;
                            }
                        }
                        if (found && wizardField.getType()==null) {
                            suggestion.append(field + " is datetime, ");
                            suggestionActions.add(field + ";type;datetime");
                            if (!suggestionReason.contains("datetime,")) {
                                suggestionReason += "datetime,";
                            }
                        }
                        if(!found) {
                            found = true;
                            for (int i = 0; i < valCOunt; i++) {
                                String val = wizardField.getValuesFound().get(i);
                                if (val.length()>10){
                                    val = val.substring(0,10);
                                }
                                java.time.LocalDate date = DateUtils.isADate(val);
                                if (date == null) {
                                    found = false;
                                    break;
                                }
                            }
                            if (found && wizardField.getType() == null) {
                                suggestion.append(field + " is date, ");
                                suggestionActions.add(field + ";type;date");
                                if (!suggestionReason.contains("date,")) {
                                    suggestionReason += "date,";
                                }
                            }
                        }
                        found = true;
                        for (int i = 0; i < valCOunt; i++) {
                            try {
                                int j = Integer.parseInt(wizardField.getValuesFound().get(i));
                                if (j % 15 > 0) {
                                    found = false;
                                    break;
                                }
                            } catch (Exception e) {
                                found = false;
                                break;

                            }
                        }
                        if (found && wizardField.getType()==null) {
                            suggestion.append(field + " is time, ");
                            suggestionActions.add(field + ";type;time");
                            String timeString = "time(data is integral multiples of 15),";
                            if (!suggestionReason.contains(timeString)) {
                                suggestionReason += timeString;

                            }
                        }
                    }
                    if (suggestionActions.size()>0){
                        wizardInfo.setSuggestionActions(suggestionActions);
                        model.put("suggestion",suggestion);
                        model.put("suggestionReason", suggestionReason);
                        return;

                    }
                    break;
                case 4:
                    String parent = null;
                    if (wizardInfo.getImportFileData()!=null) {
                        int maxCount = 0;
                        for (String field : wizardInfo.getFields().keySet()) {
                            WizardField wizardField = wizardInfo.getFields().get(field);
                            if (wizardField.getParent() != null) {
                                break;//only suggest if no data fields are already defined
                            }
                            if ("key field name".equals(wizardField.getType())) {
                                parent = wizardField.getName() + " data";
                            }
                            if (wizardField.getValuesFound().size() > maxCount) {
                                maxCount = wizardField.getValuesFound().size();

                            }
                        }
                        List<String> possibleData = new ArrayList<>();
                        for (String field : wizardInfo.getFields().keySet()) {
                            WizardField wizardField = wizardInfo.getFields().get(field);
                            if (wizardField.getValuesFound().size() * 2 >= maxCount && !Arrays.asList(TYPEOPTIONS).contains(wizardField.getType())) {//the number of different instances is more than half the max number found
                                possibleData.add(field);
                                if (!NumberUtils.isNumber(wizardField.getValuesFound().get(0)) && parent == null) {
                                    parent = "Measures";
                                }
                            }
                        }
                        if (parent == null) parent = "Values";
                        if (possibleData.size() > 0) {
                            suggestion.append("possible data fields are ");
                            for (String field : possibleData) {
                                suggestion.append(wizardInfo.getFields().get(field).getName() + ",");
                                suggestionActions.add(field + ";parent;" + parent);
                            }
                            suggestionReason = "These are the fields with the most values (excepting time and date fields). <br/> You can accept the set, then change the dataset name at the top if you want something more specific";
                            if (parent.equals("Measures")) {
                                suggestionReason += "<br/>Please note that some of the data fields are not numbers, so cannot be added together";
                                suggestionReason += "<br/>The suggestion for the data type has been filled in as " + parent + ". You can change this if you feel that another name would be better.";
                            }
                        }
                    }else{
                        //flat file suggestions
                         parent = "Values";
                         if (idFieldName!=null){
                             parent = idFieldName.substring(0,idFieldName.length()-2).trim() + " Values";
                         }
                        suggestion.append("possible data fields are: ");
                        for (String field:wizardInfo.getFields().keySet()){
                            WizardField wizardField = wizardInfo.getFields().get(field);
                            if (!field.toLowerCase(Locale.ROOT).endsWith("id") && !"key field name".equals(wizardField.getType()) && areNumbers(wizardField.getValuesFound())){
                                suggestion.append(wizardInfo.getFields().get(field).getName() + ",");
                                suggestionActions.add(field + ";parent;" + parent);
                                if (idFieldName!=null){
                                    suggestionActions.add(field+";peers;{" + idFieldName + "}");
                                }
                                suggestionReason = "These are the fields with all numeric values, which are not IDs. <br/> You can accept the set, then change the dataset name at the top if you want something more specific";
                            }
                        }
                        if(suggestionActions.size()==0){
                            if (idFieldName!=null) {
                                String countName = idFieldName.substring(0, idField.length() - 2).trim() + " count";
                                suggestion = new StringBuffer();
                                suggestion.append("No data fields found.  Suggest adding a data field: " + countName);
                                suggestionActions.add(countName + ";parent;" + parent);
                                suggestionActions.add("1;peers;{" + idFieldName +"}");
                                suggestionReason = "It is easier to create reports if you have at least one data value, defined by the ID field";
                            }
                        }
                    }
                    if (suggestionActions.size()>0){
                        wizardInfo.setSuggestionActions(suggestionActions);
                        model.put("suggestion",suggestion);
                        model.put("suggestionReason", suggestionReason);
                        wizardInfo.setLastDataField(parent);
                        model.put("dataparent", parent);
                        return;

                    }
                    break;
                case 5:
                    parent = null;
                    if (wizardInfo.getImportFileData()!=null) {
                        List<String> potentialPeers = new ArrayList<>();
                        String dataPath = null;
                        for (String field : wizardInfo.getFields().keySet()) {
                            WizardField wizardField = wizardInfo.getFields().get(field);
                            if (wizardField.getParent() != null) {
                                parent = wizardField.getParent();
                                dataPath = field.substring(0, field.lastIndexOf(ImportService.JSONFIELDDIVIDER + ""));
                                break;
                            }
                        }
                        while (dataPath.contains(ImportService.JSONFIELDDIVIDER)) {
                            List<String> peersAtThisLevel = new ArrayList<>();
                            for (String field : wizardInfo.getFields().keySet()) {
                                WizardField wizardField = wizardInfo.getFields().get(field);
                                if (wizardField.getParent() == null && field.startsWith(dataPath) && !field.substring(dataPath.length() + 1).contains(ImportService.JSONFIELDDIVIDER) && wizardField.getDistinctCount() > 1) {
                                    peersAtThisLevel.add(field);
                                }
                            }
                            if (peersAtThisLevel.size() == 1) {
                                potentialPeers.add(peersAtThisLevel.get(0));
                            } else {
                                for (String field2 : peersAtThisLevel) {
                                    if (field2.toLowerCase(Locale.ROOT).endsWith("name")) {
                                        potentialPeers.add(field2);
                                    }
                                }
                            }
                            dataPath = dataPath.substring(0, dataPath.lastIndexOf(ImportService.JSONFIELDDIVIDER + ""));
                        }
                        if (potentialPeers.size() > 0) {
                            for (String potentialPeer : potentialPeers) {
                                suggestion.append(wizardInfo.getFields().get(potentialPeer).getName() + ",");
                            }
                            for (String field : wizardInfo.getFields().keySet()) {
                                WizardField wizardField = wizardInfo.getFields().get(field);
                                if (parent.equals(wizardField.getParent())) {
                                    suggestionActions.add(field + ";peers;{" + suggestion.toString() + "}");
                                }
                            }
                            suggestionReason = "These are all within the same path as the data fields, and have multiple values";
                            suggestionReason += "<br/>There may be others";
                        }
                    }else{
                        //TODO select peers on flat files which do not have an ID
                    }
                    if (suggestionActions.size()>0){
                        wizardInfo.setSuggestionActions(suggestionActions);
                        model.put("suggestion","peers for " + parent + ": " + suggestion.toString());
                        model.put("suggestionReason", suggestionReason);
                        wizardInfo.setLastDataField(parent);
                        model.put("dataparent", parent);
                        return;

                    }
                    break;

                case 6:
                    if (wizardInfo.getImportFileData()==null){
                        Map<String,WizardField> wizardFields = wizardInfo.getFields();
                        List<String> fields = new ArrayList<>();
                        for (String field:wizardFields.keySet()){
                            WizardField wizardField = wizardInfo.getFields().get(field);
                            if (wizardField.getParent()==null && !"key field name".equals(wizardField.getType()) && !wizardField.getIgnore()){
                                fields.add(field);
                            }
                        }
                        fields.sort((o1, o2) -> {
                            return wizardFields.get(o2).getDistinctCount() - wizardFields.get(o1).getDistinctCount();
                        });
                        Set<String> attributeList = new HashSet<>();
                        for (String field:fields){
                            int potentialKeyCount = wizardFields.get(field).getDistinctCount();
                            if ("date".equals(wizardFields.get(field).getType()) || (potentialKeyCount > wizardInfo.lineCount * 0.9 && potentialKeyCount < wizardInfo.lineCount)){
                                attributeList.add(field);
                            }else {
                                if (!attributeList.contains(field) && potentialKeyCount > 1) {

                                    for (String field2 : fields) {
                                        if (!field2.equals(field) && wizardFields.get(field2).getDistinctCount() <= potentialKeyCount && wizardFields.get(field2).getDistinctCount() > potentialKeyCount * 0.5) {
                                            if (isAttribute(wizardInfo, field, field2)) {
                                                attributeList.add(field2);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        fields.removeAll(attributeList);
                        fields.sort((o1, o2) -> {
                            return wizardFields.get(o1).getDistinctCount() - wizardFields.get(o2).getDistinctCount();
                        });
                        for (String field:fields){

                            int potentialParentCount = wizardFields.get(field).getDistinctCount();
                            if (potentialParentCount>1 && wizardFields.get(field).getType()==null){

                                for (String field2:fields){
                                    if (wizardFields.get(field2).getDistinctCount()>=potentialParentCount*2){
                                        if (isParentChild(wizardInfo, field, field2)){
                                            suggestion.append(wizardFields.get(field).getName()+" is parent of " + wizardFields.get(field2).getName()+", ");
                                            suggestionActions.add(field+";child;" + field2);
                                            break;
                                        }
                                    }
                                }
                            }
                        }
                        if (suggestionActions.size()>0){
                            suggestionReason = "the names in the children are associated generally with only one parent";
                            wizardInfo.setSuggestionActions(suggestionActions);
                            model.put("suggestion",suggestion);
                            model.put("suggestionReason", suggestionReason);

                        }



                    }
                    break;
                case 7:
                    if (wizardInfo.getImportFileData()==null){
                        Map<String,WizardField> wizardFields = wizardInfo.getFields();
                        List<String> fields = new ArrayList<>();
                        for (String field:wizardFields.keySet()){
                            fields.add(field);
                        }
                        fields.sort((o1, o2) -> {
                            return wizardFields.get(o2).getDistinctCount() - wizardFields.get(o1).getDistinctCount();
                        });
                        Set<String> attributeList = new HashSet<>();
                        for (String field:fields){
                            int potentialKeyCount = wizardFields.get(field).getDistinctCount();
                            if (!"date".equals(wizardFields.get(field).getType()) && !attributeList.contains(field) && potentialKeyCount > 1) {

                                for (String field2 : fields) {
                                    if (!field2.equals(field) && wizardFields.get(field2).getDistinctCount() <= potentialKeyCount && wizardFields.get(field2).getDistinctCount() > potentialKeyCount * 0.5) {
                                        if (isAttribute(wizardInfo, field, field2)) {
                                            suggestion.append(field2 + " is an attribute of " + field + ", ");
                                            suggestionActions.add(field2 + ";anchor;" + field);
                                            attributeList.add(field2);
                                        }
                                    }
                                }
                            }
                        }
                            if (suggestionActions.size()>0){
                            wizardInfo.setSuggestionActions(suggestionActions);
                            model.put("suggestion",suggestion);
                            model.put("suggestionReason", "each value for the anchor is associated with a given attribute");

                        }



                    }



            }


        } catch (Exception e) {
            model.put("error", e.getMessage());
        }
    }


    private static boolean isAttribute(WizardInfo wizardInfo, String field, String field2){
        List<String> maybeKey = wizardInfo.getFields().get(field).getValuesFound();
        List<String> maybeAttribute = wizardInfo.getFields().get(field2).getValuesFound();
        Map<String, Set<String>> mapping = new HashMap<>();
        for (int i=0;i<maybeKey.size();i++){
            Set<String> map = mapping.get(maybeKey.get(i));
            if (map==null){
                mapping.put(maybeKey.get(i),new HashSet<>());
                map = mapping.get(maybeKey.get(i));
            }
            map.add(maybeAttribute.get(i));
        }
        for (String parent:mapping.keySet()){
            if (mapping.get(parent).size() > 1){
                return false;
            }
        }
        return true;


    }

    private static boolean isParentChild(WizardInfo wizardInfo, String field, String field2){
        List<String> maybeParent = wizardInfo.getFields().get(field).getValuesFound();
        List<String> maybeChild = wizardInfo.getFields().get(field2).getValuesFound();
        Map<String, Set<String>> mapping = new HashMap<>();
        for (int i=0;i<maybeParent.size();i++){
            if(maybeParent.get(i).length() > 0) {
                  if (maybeChild.get(i).length() == 0) {
                    return false;
                }
                Set<String> map = mapping.get(maybeParent.get(i));
                if (map == null) {
                    mapping.put(maybeParent.get(i), new HashSet<>());
                    map = mapping.get(maybeParent.get(i));
                }
                map.add(maybeChild.get(i));
            }
        }
        int totalCount = 0;
        for (String parent:mapping.keySet()){
            totalCount +=mapping.get(parent).size();
        }
        //checking that each of the potential children occurs in only one parent set
        if (totalCount< wizardInfo.getFields().get(field2).getDistinctCount()*1.01){
       //allowing for some mis-categorisation (1%)
            return true;
        }
        return false;
    }



    private static boolean areNumbers(List<String> values){
        for (String value:values){
            try{
                double d = Double.parseDouble(value);
            }catch (Exception e){
                return false;
            }
        }
        return true;
    }



    public static Map<String, WizardField> readCSVFile(String path, String fileEncoding) throws IOException {
        //adaptes from 'getLinesWithValuesInColumn from dsImportService
        char delimiter = ',';
//        System.out.println("get lines with values and column, col index : " + columnIndex);
//        System.out.println("get lines with values and column, values to check : " + valuesToCheck);
        try (BufferedReader br = Files.newBufferedReader(Paths.get(path), StandardCharsets.UTF_8)) {
            // grab the first line to check on delimiters
            String firstLine = br.readLine();
            if (firstLine.contains("|")) {
                delimiter = '|';
            }
            if (firstLine.contains("\t")) {
                delimiter = '\t';
            }
        }

        CsvMapper csvMapper = new CsvMapper();
        csvMapper.enable(CsvParser.Feature.WRAP_AS_ARRAY);
        CsvSchema schema = csvMapper.schemaFor(String[].class)
                .withColumnSeparator(delimiter)
                .withLineSeparator("\n");
        //if (delimiter == '\t') {
        //    schema = schema.withoutQuoteChar();
        //}
        MappingIterator<String[]> lineIterator;
        if (fileEncoding != null) {
            // so override file encoding.
            lineIterator = csvMapper.readerFor(String[].class).with(schema).readValues(new InputStreamReader(new FileInputStream(path), fileEncoding));
        } else {
            lineIterator = (csvMapper.readerFor(String[].class).with(schema).readValues(new File(path)));
        }
        Map<Integer, List<String>> data = new HashMap<>();
        // ok, run the check
        boolean firstLine = true;
        String[] fields = null;
        while (lineIterator.hasNext()) {
            if (firstLine) {
                fields = lineIterator.next();
                for (int count = 0; count < fields.length; count++)
                    data.put(count, new ArrayList<>());
                firstLine = false;
            } else {
                String[] dataline = lineIterator.next();
                int count = 0;
                if (dataline.length > 0) {
                    for (String value : dataline) {
                        if (count < fields.length) {
                            data.get(count++).add(value);

                        }

                    }
                    while (count < fields.length) {
                        data.get(count++).add("");
                    }
                }
            }
        }
        Map<String,WizardField>toReturn = new LinkedHashMap<>();
        int count = 0;
        for (String field:fields){
            WizardField wizardField = new WizardField(field, insertSpaces(field),false);
            wizardField.setValuesFound(data.get(count++));
            setDistinctCount(wizardField);
            toReturn.put(field,wizardField);
        }

        return toReturn;
    }




}
