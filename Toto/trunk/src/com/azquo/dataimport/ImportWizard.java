package com.azquo.dataimport;

import com.azquo.DateUtils;
import com.azquo.StringLiterals;
import com.azquo.admin.business.Business;
import com.azquo.spreadsheet.LoggedInUser;
import com.azquo.spreadsheet.transport.UploadedFile;
import com.azquo.spreadsheet.zk.BookUtils;
import com.csvreader.CsvWriter;
import io.keikai.api.Importers;
import io.keikai.api.model.Book;
import io.keikai.api.model.Sheet;
import io.keikai.model.SName;
import org.apache.commons.lang.math.NumberUtils;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.ss.formula.functions.FreeRefFunction;
import org.apache.poi.ss.formula.functions.Value;
import org.apache.poi.ss.formula.udf.AggregatingUDFFinder;
import org.apache.poi.ss.formula.udf.DefaultUDFFinder;
import org.apache.poi.ss.formula.udf.UDFFinder;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.AreaReference;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.jfree.data.Values;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.ui.ModelMap;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.Normalizer;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Pattern;

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


    public static final String[] TYPEOPTIONS = {"key field id","key field name", "date","time","datetime"};

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
                    wizardField.setValuesFound(new ArrayList<>());
                    if (testItem==null || testItem.length()==0) {
                        for (int i = 0; i < 20; i++) {
                            wizardField.getValuesFound().add(wizardField.getValuesOnFile().get(i));
                        }
                    }
                    if (!wizardField.getIgnore()){
                        list.put(field,wizardField);
                    }
                }
                if (testField!=null && testField.length() > 0){
                    WizardField wizardField = wizardInfo.getFields().get(testField);
                    for (int i=0;i<wizardInfo.getLineCount();i++){
                        if (wizardField.getValuesOnFile().get(i).equals(testItem)){
                            for (String field:wizardInfo.getFields().keySet()){
                                WizardField wizardField1 = wizardInfo.getFields().get(field);
                                wizardField1.getValuesFound().add(wizardField1.getValuesOnFile().get(i));

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
        for (String field:wizardInfo.getFields().keySet()){
            wizardInfo.getFields().get(field).setValuesFound(null);
        }
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
            JSONObject jsonObject = new JSONObject(wizardInfo.getImportFileData());//remove line feeds
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
                    WizardField wizardField = new WizardField(field,suggestedName,false);
                    wizardField.setValuesFound(fieldsFound.get(field));
                    wizardInfo.getFields().put(field,wizardField);
                }
            }else{
                for (String field:fieldsFound.keySet()){
                      wizardInfo.getFields().get(HTMLify(field)).setValuesFound(fieldsFound.get(field));
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
                   wizardField = new WizardField(loadedField,azquoName,true);
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
                String dataParent = getClause("datatype", clauses);
                wizardField.setParent(dataParent);
                String peersString = getClause("peers", clauses);
                if (peersString!=null){
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
                setInterpretation(wizardInfo,wizardField);
                row++;
                // todo don't show admins if not admin
            }
            calcXL(loggedInUser.getWizardInfo());
            return "";
        }catch(Exception e){
            return e.getMessage();
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

        String peerType = null;
        if (peerHeadings.size() > 0){
            peerType ="peer for " + peerHeadings.get(0);
            for (int peer = 1;peer<peerHeadings.size();peer++){
                peerType +="," +peerHeadings.get(peer);
            }
        }
        String type = wizardField.getType();

        if (wizardField.getChild()!=null){
            if (type==null) {
                type = "";
            }else{
                type = type + ";";
             }
            type +="parent of " + wizardInfo.getFields().get(wizardField.getChild()).getName();
        }
        if (wizardField.getAnchor()!=null){
            type = "attribute of " + wizardInfo.getFields().get(wizardField.getAnchor()).getName();
        }

        if (peerType!=null && type==null){
            type=peerType;
            peerType = null;

        }
        if (wizardField.getParent()!=null){
            type ="datatype " + wizardField.getParent();
        }



        if (type==null ) {

            wizardField.setInterpretation(checkForParents(wizardInfo, wizardField));

            return;
        }

        toReturn.append(";"+type);
         if (peerType!=null){
            toReturn.append(";"+ peerType);
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
           if (toReturn.length()>0){
            wizardField.setInterpretation(toReturn.toString().substring(1));

        }
        else{
            wizardField.setInterpretation(checkForParents(wizardInfo,wizardField));
        }
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
               if (wizardInfo.getFields().get(field).getParent() != null) {
                    dataField = field;
               }
               if (!field.contains(" where ")){
                   pathsRequired.add(ImportService.JSONFIELDDIVIDER + field);
               }
               if (wizardField.getValuesOnFile()!=null){
                    flatfileData.put(field,wizardField.getValuesOnFile());
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

                traverseJSON3(flatfileData, pathsRequired, onePassValues, "", new JSONObject(wizardInfo.getImportFileData()), jsonTests, null);
            } catch (Exception e) {
                throw e;
            }
        }
        /*
        START HERE
        the field names are in wizardInfo - Azquo names as '.name'
        The value array is 'flatfiledata'
         Use the 'interpretation'.
           NEW CLAUSES:
         'date' 'time' 'datetime'  - the type of the data - I'll put it into standard format before it reaches you.
         'datatype <DT>'    equivalend to 'child of <DT>, where <DT> is also child of 'Data'
         'peer for' and 'parent of'   also include 'child of <field.name>.  May also be worth adding 'language <field.name>'
         Dates.   check max and min, and create a setup sheet for Years, Months, Days to cover the required period
         Needed:
         A database and one or more reports.


*/

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
        try {
            boolean firstRow = true;
            for (org.apache.poi.ss.usermodel.Row row : sheet) {
                int col = 0;
                for (Iterator<org.apache.poi.ss.usermodel.Cell> ri = row.cellIterator(); ri.hasNext(); ) {
                    org.apache.poi.ss.usermodel.Cell cell = ri.next();
                    while (cell.getColumnIndex() > col){
                        wizardFields.get(colMap.get(col++)).getValuesOnFile().add("");
                    }
                    String value = ImportService.getCellValueUS(cell, usDates);
                    if (value==null) value = "";
                    if (firstRow){
                        colMap.put(cell.getColumnIndex(),value);
                        WizardField wizardField = new WizardField(value,value,false);
                        wizardField.setValuesOnFile(new ArrayList<>());
                        wizardFields.put(value,wizardField);
                    }else{
                        wizardFields.get(colMap.get(cell.getColumnIndex())).getValuesOnFile().add(value);
                    }
                    col++;
                }
                firstRow = false;
                lineCount++;
            }
        }catch (Exception e){
            throw e;
        }
        return lineCount;
    }


    public static void calcXL(WizardInfo wizardInfo)throws Exception{

        for (String maybeXL:wizardInfo.getFields().keySet()){
            if(maybeXL.toLowerCase(Locale.ROOT).startsWith("az=")){
                WizardField xlField = wizardInfo.getFields().get(maybeXL);

                Map<String,String> fieldList = new HashMap<>();
                for (String field:wizardInfo.getFields().keySet()){
                    WizardField wizardField = wizardInfo.getFields().get(field);
                    if (xlField.getImportedName().contains(StringLiterals.QUOTE + wizardField.getName() + StringLiterals.QUOTE)){
                        fieldList.put(StringLiterals.QUOTE + wizardField.getName() + StringLiterals.QUOTE, field);
                    }
                }
                xlField.setValuesOnFile(new ArrayList<>());
                for(int i=0;i <wizardInfo.getLineCount();i++){
                    String compositionPattern = xlField.getImportedName().substring(3);
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
                    xlField.getValuesOnFile().add(compositionPattern);
                }
            }
        }

    }

    public static String getXLTerm(WizardInfo wizardInfo, String field, int i){
     String sourceVal =  wizardInfo.getFields().get(field).getValuesOnFile().get(i);
     if (NumberUtils.isNumber(sourceVal)){
         return sourceVal;
     }
     return "\"" + sourceVal + "\"";

}

}
