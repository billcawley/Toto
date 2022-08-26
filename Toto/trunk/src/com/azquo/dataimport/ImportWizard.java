package com.azquo.dataimport;

import com.azquo.DateUtils;
import com.azquo.StringLiterals;
import com.azquo.admin.controller.ManageDatabasesController;
import com.azquo.rmi.RMIClient;
import com.azquo.spreadsheet.LoggedInUser;
import com.azquo.spreadsheet.SpreadsheetService;
import com.azquo.spreadsheet.transport.UploadedFile;
import com.azquo.spreadsheet.zk.BookUtils;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvParser;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import net.lingala.zip4j.core.ZipFile;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.math.NumberUtils;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.AreaReference;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.XML;
import org.springframework.ui.ModelMap;
import org.springframework.web.multipart.MultipartFile;
import org.zeroturnaround.zip.ZipException;
import org.zeroturnaround.zip.ZipUtil;

import javax.servlet.http.HttpServletRequest;
import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

import static org.apache.poi.ss.usermodel.CellType.NUMERIC;
import static org.apache.poi.ss.usermodel.CellType.STRING;

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
            this.source = ImportService.JSONFIELDDIVIDER + jsonField.substring(0, lastFieldPos);
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

    public static final String KEYFIELDID = "key field id";
    public static final String KEYFIELDNAME = "key field name";
    public static final int NAMESTAGE = 1;
    public static final int TYPESTAGE = 2;
    public static final int DATASTAGE = 3;
    public static final int PARENTSTAGE = 4;


    public static final String[] TYPEOPTIONS = {KEYFIELDID, KEYFIELDNAME, HeadingReader.DATELANG, HeadingReader.USDATELANG, "time", "datetime"};

    private static int nthLastIndexOf(int nth, String ch, String string) {
        if (nth <= 0) return string.length();
        if (string.lastIndexOf(ch) == -1) return -1;//start of string
        return nthLastIndexOf(--nth, ch, string.substring(0, string.lastIndexOf(ch)));

    }

    public static Map<String, WizardField> getDataValuesFromFile(WizardInfo wizardInfo, String testField, String testItem) throws Exception {

        if (wizardInfo.getImportFileData() != null) {
            Map<String, WizardField> list = readJsonFile(wizardInfo, testField, testItem);
            if (testItem == null) {
                for (String field : wizardInfo.getFields().keySet()) {
                    WizardField wizardField = wizardInfo.getFields().get(field);
                    if (wizardField.getAdded()) {
                        list.put(field, wizardField);
                    }
                }
            }
            return list;

        } else {
            Map<String, WizardField> list = new LinkedHashMap<>();
            boolean found = false;
            for (String field : wizardInfo.getFields().keySet()) {
                WizardField wizardField = wizardInfo.getFields().get(field);
                if (wizardField.getSelect()) {
                    list.put(field, wizardField);
                }
            }
            if (testField != null && testField.length() > 0) {
                WizardField wizardField = wizardInfo.getFields().get(testField);
                for (int i = 0; i < wizardInfo.getLineCount(); i++) {
                    if (wizardField.getValuesFound().get(i).equals(testItem)) {
                        for (String field : wizardInfo.getFields().keySet()) {
                            WizardField wizardField1 = wizardInfo.getFields().get(field);
                            wizardField1.setValueFound(wizardField1.getValuesFound().get(i));

                        }
                        break;
                    }
                }
            }
            return list;
        }

    }


    public static boolean isWizardSetup(LoggedInUser loggedInUser, UploadedFile uploadedFile) {
        if (uploadedFile.getFileName().toLowerCase(Locale.ROOT).contains("azquo setup.")){
            return true;
        }
      return false;
    }


    public static Map<String,WizardField> readJsonFile(WizardInfo wizardInfo, String testField, String testItem)throws Exception  {
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

        if (HeadingReader.DATELANG.equals(wizardField.getType()) || HeadingReader.USDATELANG.equals(wizardField.getType()) ){
            wizardField.setValuesFound(adjustDates(values, wizardField.getType()));
        }else{
            if ("time".equals(wizardField.getType())){
                wizardField.setValuesFound(adjustTImes(values));
            }else{
                wizardField.setValuesFound(values);
            }
        }
        setDistinctCount(wizardField);
    }

    private static List<String> adjustDates(List<String> values, String dateType) {
        List<String >toReturn = new ArrayList<>();
        for (String value : values) {
            if (value.length() > 10) {
                value = value.substring(0, 10);
            }
            try {
                if (dateType.equals(HeadingReader.DATELANG)){
                    DateUtils.isADate(value);
                }else{
                    DateUtils.isUSDate(value);
                }
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
                wizardInfo.getFields().get(field).setSelect(false);
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
               wizardField.setSelect(true);
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

    private static boolean usedName(WizardInfo wizardInfo, String name){
        for (String field:wizardInfo.getFields().keySet()){
            if (wizardInfo.getFields().get(field).getName().equalsIgnoreCase(name)){
                return true;
            }
        }
        if (name.equals(wizardInfo.getLastDataField())){
            return true;
        }
        return false;

    }

    public static String processFound(WizardInfo wizardInfo, String fields, int stage, String dataParent) {
        if (fields.length()==0){
            return null;
        }
        String[] fieldLines = fields.split("\n");
        switch (stage) {
            case NAMESTAGE:
                List<String> toDelete = new ArrayList<>();
                for (String line : fieldLines) {
                    String[] elements = line.split("\t");
                    if (elements[0].length() > 0) {
                        WizardField wizardField = wizardInfo.getFields().get(elements[0]);
                        if (wizardField == null) {
                            wizardField = new WizardField(elements[0], elements[2], true);
                            wizardInfo.getFields().put(elements[0], wizardField);
                            try {
                                calcXL(wizardInfo);
                            } catch (Exception e) {

                            }
                        }
                        //imported name, values, name, selected
                        String newName = elements[2];
                        if (!wizardField.getName().equalsIgnoreCase(newName)) {
                            if (usedName(wizardInfo, newName)) {
                                return "error: duplicate name " + newName;
                            }
                        }
                        wizardField.setName(newName);
                        if (elements.length > 3 && !elements[3].equals("true")) {
                            if (wizardField.getAdded()) {
                                toDelete.add(wizardField.getImportedName());
                            }
                            wizardField.setSelect(false);
                        } else {
                            wizardField.setSelect(true);
                        }
                    }
                }
                for (String field:toDelete){
                    wizardInfo.getFields().remove(field);
                }
                break;
            case TYPESTAGE:
                for (String line : fieldLines) {
                    String[] elements = line.split("\t");
                    WizardField wizardField = wizardInfo.getFields().get(findFieldFromName(wizardInfo, elements[0]));
                    //imported name, values, name, selected
                    if (elements.length < 3){
                        wizardField.setType(null);
                    }else{
                        wizardField.setType(elements[2]) ;
                    }
                }
                break;

            case DATASTAGE:
                if (dataParent == null) {
                    return null;
                }
                List<String> peers = new ArrayList<>();
                String peerParent = dataParent;
                if (wizardInfo.getLastDataField()!=null) {
                    peerParent = wizardInfo.lastDataField;
                }

                for (String field:wizardInfo.getFields().keySet()){
                    WizardField wizardField = wizardInfo.getFields().get(field);
                    if (peerParent.equals(wizardField.getParent())){
                        peers = wizardField.getPeers();
                        break;
                    }
                }
                if (wizardInfo.getLastDataField()!=null) {
                    for (String field : wizardInfo.getFields().keySet()) {
                        WizardField wizardField = wizardInfo.getFields().get(field);
                        if (wizardInfo.getLastDataField().equals(wizardField.getParent())) {
                            wizardField.setParent(null);
                            wizardField.setPeers(null);
                        }
                    }
                }
                wizardInfo.setLastDataField(dataParent);
                //fieldName, valuesFound, checkEntry"
                for (String line:fieldLines){
                    String[]fieldInfo = line.split("\t");
                    if ("true".equals(fieldInfo[2])){
                        WizardField wizardField = wizardInfo.getFields().get(findFieldFromName(wizardInfo,fieldInfo[0]));
                        wizardField.setParent(dataParent);
                        wizardField.setPeers(peers);
                    }

                }
                peers = new ArrayList<>();
                //peers
                //fieldName, valuesFound, checkEntry"
                for (String line:fieldLines){
                    String[]fieldInfo = line.split("\t");
                    if ("true".equals(fieldInfo[3])){
                        peers.add(findFieldFromName(wizardInfo,fieldInfo[0]));
                     }

                }
                for (String field:wizardInfo.getFields().keySet()){
                    WizardField wizardField = wizardInfo.getFields().get(field);
                    if (dataParent.equals(wizardField.getParent())){
                        wizardField.setPeers(peers);
                    }
                }
                 break;
            case PARENTSTAGE:
                for (String line:fieldLines){
                    String[]fieldInfo = line.split("\t");
                    //parent child
                    WizardField wizardField = wizardInfo.getFields().get(findFieldFromName(wizardInfo, fieldInfo[0]));
                    if (fieldInfo.length>1) {
                        if (fieldInfo[1].length() > 0 && !isKeyField(wizardField) && wizardField.getParent() == null) {
                            wizardField.setChild(findFieldFromName(wizardInfo, fieldInfo[1]));
                            wizardField.setAnchor(null);
                        } else {
                            wizardField.setChild(null);
                        }
                    }
                    if (fieldInfo.length>2) {

                        //anchor attribute
                        if (fieldInfo[2].length() > 0 && wizardField.getParent() == null) {
                            wizardField.setAnchor(findFieldFromName(wizardInfo, fieldInfo[2]));
                            wizardField.setChild(null);
                        } else {
                            wizardField.setAnchor(null);

                        }
                    }
                }



        }
        for (String heading : wizardInfo.getFields().keySet()) {
            WizardField wizardField = wizardInfo.getFields().get(heading);
            setInterpretation(wizardInfo, wizardField);
        }

        return null;
    }


    private static boolean isKeyField(WizardField wizardField){
        if (KEYFIELDNAME.equals(wizardField.getType())) return true;
        if (KEYFIELDID.equals(wizardField.getType())) return true;
        return false;
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
        if (wizardField.getAnchor()!=null){
            toReturn.append(";attribute of " + wizardInfo.getFields().get(wizardField.getAnchor()).getName());
        }

        if (wizardField.getParent()!=null){
            toReturn.append(";datagroup " + wizardField.getParent());
        }

        if (HeadingReader.DATELANG.equals(wizardField.getType())){
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
            if (wizardField.getSelect() && wizardField.getInterpretation().length() > 0) {
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
        int fieldCount = 0;
        while (fieldCount < wizardInfo.getFields().size()){//Not using standard loop as the set of wizardFields may expand.

            List<String> fields = new ArrayList<>();
            for (String field:wizardInfo.getFields().keySet()){
                fields.add(field);
            }
            String field = fields.get(fieldCount++);
            WizardField wizardField = wizardInfo.getFields().get(field);
            if (KEYFIELDID.equals(wizardField.getType())){
                idField = field;
            }

            if (KEYFIELDNAME.equals(wizardField.getType())){
                if (wizardField.getAnchor()!=null){
                      //create new field so that this field can be the attribute of two others
                    WizardField wizardField1 = new WizardField(wizardField.getImportedName() + "-copy", wizardField.getName(),true);
                    wizardField1.setAnchor(wizardField.getAnchor());
                    wizardInfo.getFields().put(wizardField.getImportedName() + "-copy", wizardField1);
                    setInterpretation(wizardInfo,wizardField1);
                    flatfileData.put(wizardField1.getImportedName(),wizardField.getValuesFound());
                }
                  wizardField.setAnchor(idField);
                setInterpretation(wizardInfo,wizardField);



            }
            if (HeadingReader.DATELANG.equals(wizardField.getType())||HeadingReader.USDATELANG.equals(wizardField.getType())){
                flatfileData.put(field,adjustDates(flatfileData.get(field), wizardField.getType()));
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

        RMIClient.getServerInterface(loggedInUser.getDatabaseServer().getIp()).uploadWizardData(loggedInUser.getDataAccessToken(),wizardInfo.importFileName, wizardDefs , data);



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


    public static String makeSuggestion(WizardInfo wizardInfo, int stage) {
        List<String> suggestions = new ArrayList<>();
        wizardInfo.setLastDataField(null);
        String suggestionReason = "";
        String idField = null;
        String idFieldName = null;
        boolean usDate = false;
        for (String field : wizardInfo.getFields().keySet()) {
            WizardField wizardField = wizardInfo.getFields().get(field);
            if (KEYFIELDID.equals(wizardField.getType())) {
                idField = field;
                idFieldName = wizardField.getName();
            }
        }

        try {
            boolean hasSuggestion = false;
             switch (stage) {
                 case NAMESTAGE:

                    //check names for short names
                    Set<String> shortNames = new HashSet<>();
                    for (String field : wizardInfo.getFields().keySet()) {
                        WizardField wizardField = wizardInfo.getFields().get(field);
                        if (!wizardField.getImportedName().equals(wizardField.getName())){
                             suggestionReason = "we have made the names more friendly";
                        }
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
                                    Map<String, WizardField> list = getDataValuesFromFile(wizardInfo, field, shortName);
                                    if (list != null) {
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
                                if (fullNameField != null) {
                                    for (String shortName : fieldData) {
                                        Map<String, WizardField> list = getDataValuesFromFile(wizardInfo, field, shortName);
                                        String fullName = list.get(fullNameField).getValueFound();
                                        wizardField.setName(fullName);
                                        suggestionReason = "we have found a key table for the name abbreviations";
                                        //suggestionActions.add(findFieldFromName(wizardInfo, shortName) + ";name;" + fullName);
                                        //suggestions.add(shortName + "->" + fullName );
                                    }
                                }
                            }
                        }
                    }
                    return suggestionReason;

                 case TYPESTAGE:
                    //dates times, key fields
                    idField = wizardInfo.getFields().keySet().iterator().next();

                    if (idField.toLowerCase(Locale.ROOT).endsWith("id")||idField.toLowerCase(Locale.ROOT).endsWith("key")) {
                        WizardField wizardField = wizardInfo.getFields().get(idField);
                        wizardField.setType(KEYFIELDID);
                        hasSuggestion = true;

                    } else {
                        idField = null;
                    }
                    if (idField != null) {
                        for (String field : wizardInfo.getFields().keySet()) {
                            if (field.toLowerCase(Locale.ROOT).endsWith("name")) {
                                wizardInfo.getFields().get(field).setType(KEYFIELDNAME);
                                hasSuggestion = true;
                                break;
                            }
                        }
                    }

                    for (String field : wizardInfo.getFields().keySet()) {
                        WizardField wizardField = wizardInfo.getFields().get(field);
                        int valCOunt = wizardField.getValuesFound().size();
                        if (valCOunt > 100) {
                            valCOunt = 100;

                        }
                        boolean found = true;
                        for (int i = 0; i < valCOunt; i++) {
                            String val = wizardField.getValuesFound().get(i);
                            if (val.length()>0){
                                java.time.LocalDate date = DateUtils.isDateTime(val);
                                if (date == null) {
                                    found = false;
                                    break;
                                }
                            }
                        }
                        if (found && wizardField.getType() == null) {
                            wizardField.setType("datetime");
                            hasSuggestion = true;
                        }
                        if (!found) {
                            found = true;
                             for (int i = 0; i < valCOunt; i++) {
                                String val = wizardField.getValuesFound().get(i);
                                if (val.length() > 10) {
                                    val = val.substring(0, 10);
                                }
                                if (val.length()>0) {
                                    java.time.LocalDate date = DateUtils.isADate(val);
                                    if (date == null) {
                                        date = DateUtils.isUSDate(val);
                                        if (date==null) {
                                            found = false;
                                            break;
                                        }else{
                                            usDate = true;
                                        }
                                    }
                                }
                            }
                            if (found && wizardField.getType() == null) {
                                if (usDate){
                                    wizardField.setType(HeadingReader.USDATELANG);
                                }else{
                                    wizardField.setType(HeadingReader.DATELANG);
                                }
                                hasSuggestion = true;
                            }
                        }
                        found = true;
                        for (int i = 0; i < valCOunt; i++) {
                            try {
                                String val = wizardField.getValuesFound().get(i);
                                if (val.length()>0) {
                                    int j = Integer.parseInt(wizardField.getValuesFound().get(i));
                                    if (j % 15 > 0) {
                                        found = false;
                                        break;
                                    }
                                }
                            } catch (Exception e) {
                                found = false;
                                break;

                            }
                        }
                        if (found && wizardField.getType() == null) {
                            wizardField.setType("time");
                            hasSuggestion = true;
                        }
                    }
                    if (hasSuggestion){
                        return "we gave fiied in some suggestions";
                    }
                 case DATASTAGE:
                    String parent = null;
                    if (wizardInfo.getImportFileData() != null) {
                        int maxCount = 0;
                        for (String field : wizardInfo.getFields().keySet()) {
                            WizardField wizardField = wizardInfo.getFields().get(field);

                            if (wizardField.getParent() != null) {
                                break;//only suggest if no data fields are already defined
                            }
                            if (KEYFIELDNAME.equals(wizardField.getType())) {
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
                            for (String field : possibleData) {
                                wizardInfo.getFields().get(field).setParent(parent);
                            }
                        }
                    } else {
                        //flat file suggestions
                        parent = "Values";
                        if (idFieldName != null) {
                            parent = idFieldName.substring(0, idFieldName.lastIndexOf(" ")) + " Values";
                        }
                        //suggestion.append("possible data fields are: ");
                        for (String field : wizardInfo.getFields().keySet()) {
                            WizardField wizardField = wizardInfo.getFields().get(field);
                            if (!field.toLowerCase(Locale.ROOT).endsWith("code") && !isKeyField(wizardField) && areNumbers(wizardField.getValuesFound())) {
                                suggestions.add(wizardInfo.getFields().get(field).getName());
                                wizardField.setParent(parent);
                                if (idFieldName != null) {
                                    List<String> peers = new ArrayList<>();
                                    peers.add(idField);
                                    wizardField.setPeers(peers);
                                }
                                suggestionReason = "These are the fields with all numeric values, which are not IDs.";
                            }
                        }
                        if(suggestionReason.length()==0){
                            if (idFieldName!=null) {
                                String countName = idFieldName.substring(0, idField.length() - 2).trim() + " count";
                                suggestionReason ="No data fields found.  Suggest adding a data field: " + countName;
                                WizardField wizardField = new WizardField("1", countName, true);
                                List<String>peers = new ArrayList<>();
                                peers.add(idField);
                                wizardField.setParent(parent);;
                                wizardField.setPeers(peers);
                                wizardInfo.getFields().put("1",wizardField);
                                try{
                                    calcXL(wizardInfo);
                                }catch (Exception e){

                                }
                                  suggestionReason += "<br/>It is easier to create reports if you have at least one data value, defined by the ID field";
                            }
                        }
                    }
                    parent = null;
                    if (wizardInfo.getImportFileData() != null) {
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
                        String peerList = "";
                        if (potentialPeers.size() > 0) {
                            for (String field : wizardInfo.getFields().keySet()) {
                                WizardField wizardField = wizardInfo.getFields().get(field);
                                if (parent.equals(wizardField.getParent())) {
                                    wizardField.setPeers(potentialPeers);
                                    ;
                                }
                            }
                        }
                    } else {
                        //TODO select peers on flat files which do not have an ID
                    }
                    return suggestionReason;

                 case PARENTSTAGE:
                    if (wizardInfo.getImportFileData() == null) {
                        Map<String, WizardField> wizardFields = wizardInfo.getFields();
                        List<String> fields = new ArrayList<>();
                        for (String field : wizardFields.keySet()) {
                            fields.add(field);
                        }
                        fields.sort((o1, o2) -> {
                            return wizardFields.get(o2).getDistinctCount() - wizardFields.get(o1).getDistinctCount();
                        });
                        Set<String> attributeList = new HashSet<>();
                        for (String field : fields) {
                            WizardField wizardField = wizardInfo.getFields().get(field);
                            int potentialKeyCount = wizardField.getDistinctCount();
                            if (!HeadingReader.DATELANG.equals(wizardFields.get(field).getType()) && !attributeList.contains(field) && potentialKeyCount > 1) {

                                for (String field2 : fields) {
                                    if (!field2.equals(field) && wizardFields.get(field2).getDistinctCount() <= potentialKeyCount && wizardFields.get(field2).getDistinctCount() > potentialKeyCount * 0.5) {

                                        if (isAttribute(wizardInfo, field, field2)) {
                                            wizardFields.get(field2).setAnchor(field);
                                            suggestionReason += "<li>" + wizardFields.get(field2).getName() + " is an attribute of " + wizardField.getName() + "</li>";
                                            attributeList.add(field2);
                                       }
                                    }
                                }
                            }
                        }
                    }
                    List<String>attributeParentList= new ArrayList<>();
                    if (wizardInfo.getImportFileData() == null) {
                        Map<String, WizardField> wizardFields = wizardInfo.getFields();
                        List<String> fields = new ArrayList<>();
                        for (String field : wizardFields.keySet()) {
                            WizardField wizardField = wizardInfo.getFields().get(field);
                            if (wizardField.getParent() == null && !KEYFIELDNAME.equals(wizardField.getType()) && wizardField.getSelect()) {
                                fields.add(field);
                            }
                        }
                        fields.sort((o1, o2) -> {
                            return wizardFields.get(o2).getDistinctCount() - wizardFields.get(o1).getDistinctCount();
                        });
                        Set<String> attributeList = new HashSet<>();
                        for (String field : fields) {
                            int potentialKeyCount = wizardFields.get(field).getDistinctCount();
                            if (HeadingReader.DATELANG.equals(wizardFields.get(field).getType()) || (potentialKeyCount > wizardInfo.lineCount * 0.94 && potentialKeyCount < wizardInfo.lineCount)) {
                                attributeList.add(field);
                            } else {
                                if (!attributeList.contains(field) && potentialKeyCount > 1) {

                                    for (String field2 : fields) {
                                        if (!field2.equals(field) && wizardFields.get(field2).getDistinctCount() <= potentialKeyCount && wizardFields.get(field2).getDistinctCount() > potentialKeyCount * 0.5) {
                                            if (isAttribute(wizardInfo, field, field2)) {
                                                attributeList.add(field2);
                                                attributeParentList.add(field);
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
                        for (String field : fields) {

                            int potentialParentCount = wizardFields.get(field).getDistinctCount();
                            if (potentialParentCount > 1 && wizardFields.get(field).getType() == null) {

                                for (String field2 : fields) {
                                    if (wizardFields.get(field2).getDistinctCount() >= potentialParentCount /0.94) {
                                        if (isParentChild(wizardInfo, field, field2)) {

                                            wizardFields.get(field).setChild(field2);
                                            suggestionReason += "<li>" + wizardFields.get(field).getName() + " is a parent of " + wizardFields.get(field2).getName()+ "</li>";
                                            break;
                                        }
                                    }
                                }
                             }
                        }
                    }
                    if (suggestionReason.length()>0) {
                        return "<p>We&#x27;ve automatically identified <strong>potential</strong> relationships. Here can edit parent/children and attribute relationshipss</p><ul> " + suggestionReason + "</ul>";
                    }
                    return "";
             }


        } catch (Exception e) {
            return "Error:" + e.getMessage();
        }
        return "";
    }



    private static boolean isAttribute(WizardInfo wizardInfo, String field, String field2){
        List<String> maybeKey = wizardInfo.getFields().get(field).getValuesFound();
        List<String> maybeAttribute = wizardInfo.getFields().get(field2).getValuesFound();
        Map<String, Set<String>> mapping = new HashMap<>();
        for (int i=0;i<maybeAttribute.size();i++){
            Set<String> map = mapping.get(maybeAttribute.get(i));
            if (map==null){
                mapping.put(maybeAttribute.get(i),new HashSet<>());
                map = mapping.get(maybeAttribute.get(i));
            }
            map.add(maybeKey.get(i));
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
        String childType = wizardInfo.getFields().get(field2).getType();
        if (HeadingReader.DATELANG.equals(childType) || HeadingReader.USDATELANG.equals(childType)){
            return false;
        }
        Map<String, Set<String>> mapping = new HashMap<>();
        if (maybeParent.size() < 4){
            return false;
        }
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
        boolean found = false;
        for (String value:values) {
            if (value.length() > 0) {
                found = true;
                if (value.charAt(0) == '0' && value.indexOf(".") < 0) {
                    return false;// any 'number' starting with 0 which does not contain a decimal point can not be considered a number
                }
                try {
                    double d = Double.parseDouble(value);
                } catch (Exception e) {
                    return false;
                }
            }
        }
        return found;
    }



    public static Map<String, WizardField> readCSVFile(String path, String fileEncoding) throws IOException {
        //adaptes from 'getLinesWithValuesInColumn from dsImportService
     //   System.out.println("get lines with values and column, col index : " + columnIndex);
//        System.out.println("get lines with values and column, values to check : " + valuesToCheck);
        Charset charset = StandardCharsets.ISO_8859_1;
        if (fileEncoding == null){
            fileEncoding = "ISO_8859_1";
        }
        char delimiter = getDelimiter(path, charset);
          if (delimiter == ' ') {
           charset = StandardCharsets.UTF_8;
           delimiter = getDelimiter(path, charset);
           fileEncoding = null;
         }
        if (delimiter==' '){
            throw new IOException("cannot read file");
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
        String firstFieldName = null;
        for (String field:fields){
              WizardField wizardField = new WizardField(field, insertSpaces(field),false);
            if (firstFieldName==null){
                firstFieldName = wizardField.getName();
            }
            //try to deal with fields called 'name' by being more specific.
            if (wizardField.getName().toLowerCase(Locale.ROOT).equals("name") && (firstFieldName.toLowerCase(Locale.ROOT).endsWith(" id") || firstFieldName.toLowerCase(Locale.ROOT).endsWith(" key"))){
                wizardField.setName(firstFieldName.substring(0, firstFieldName.lastIndexOf(" ")) + " name");


            }
            wizardField.setValuesFound(data.get(count++));
            setDistinctCount(wizardField);
            toReturn.put(field,wizardField);
        }

        return toReturn;
    }

    private static char getDelimiter(String path, Charset charset){
        try{
            BufferedReader br = Files.newBufferedReader(Paths.get(path), charset);
            // grab the first line to check on delimiters
            String firstLine = br.readLine();
            if (firstLine.contains("|")) {
                return '|';
            }
            if (firstLine.contains("\t")) {
                return '\t';
            }
            return ',';
        }catch(Exception e){
            return ' ';
        }
    }

    private static List<ImportStage> createStageList(int stage){
        List<ImportStage> toReturn = new ArrayList<>();
        ImportStage importStage = new ImportStage(stage,NAMESTAGE,"Properties");
        importStage.setStageComment("rename or exclude columns");
        if (stage==NAMESTAGE) {
            importStage.setFieldHeadings("Imported name,Samples,Suggested name,");
            importStage.setFields("fieldName,valuesFound,textEntry,checkEntry");
            importStage.setInstructions("Please review the <b>suggested</b> name for each field. You can also <b>exclude</b> columns from the import by unselecting the row.");
            importStage.setTitle(importStage.getStageName());
        }
        toReturn.add(importStage);
        
        importStage = new ImportStage(stage,TYPESTAGE,"Properties");
        importStage.setStageComment("identify key fields");
        if (stage==TYPESTAGE) {
            importStage.setFields("fieldName,valuesFound,listEntry");
            importStage.setFieldHeadings("Field Name, Sample, Field type");
            importStage.setInstructions("identify key field ids, names, and data/time fields");
            importStage.setTitle(importStage.getStageName());
        }
        toReturn.add(importStage);

        importStage = new ImportStage(stage,DATASTAGE,"Data");
        importStage.setStageComment("which fields are values?");
        if (stage==DATASTAGE) {
            importStage.setFields("fieldName,valuesFound,checkEntry,check2Entry");
            importStage.setFieldHeadings("Field Name,Samples,Values,Peers");
            importStage.setTypeName("Data type");
            importStage.setInstructions("<p><strong>Values</strong> are usually additive numbers, but need not be</p> +" +
                    "<p><strong>Peers</strong> are the fields necessary to define the values</br>" +
                    "If there is a key field, it is probably the sole peer." +
                    "</br> ...but budget information may have many peers (e.g. period, division, version)" );
            importStage.setTitle(importStage.getStageName());
        }
        toReturn.add(importStage);





        importStage = new ImportStage(stage,PARENTSTAGE,"Relationships");
        importStage.setStageComment("identify parent-child relationships");
        if (stage==PARENTSTAGE) {
            importStage.setFields("fieldName,listEntry,list2Entry, example");
            importStage.setFieldHeadings("PARENT,CHILD,ANCHOR");
            importStage.setInstructions("   <p>\n" +
                    "Please define <strong>parent-child</strong> relationships.  These allow you to categorise data. " +
                    "Typical relationships include:" +
                    "</p>" +
                    " <ul>" +
                    " <li>Customer &gt; Order &gt; Order Item</li>" +
                    " <li>Country &gt; Town &gt; Street</li>" +
                    " </ul>"+
                    "<p><strong>attributes</strong> are values you want to retain, but which refer only to one other name - the <strong>anchor</strong></p>" +
                    "<ul>" +
                    "<li>telephone number is an attribute of person</li>" +
                    "<li>elevation is an attribute of location</li>" +
                    "</ul>");

            importStage.setTitle(importStage.getStageName());


        }

        toReturn.add(importStage);

        return toReturn;
    }

   private static List<ImportWizardField> createWizardFieldList(LoggedInUser loggedInUser, int stage, String chosenField, String chosenValue, String dataParent){
         Map<String,WizardField>wizardFields = loggedInUser.getWizardInfo().getFields();
       List<ImportWizardField> toReturn = new ArrayList<>();
       List<String>peers = new ArrayList<>();
       if (dataParent!=null && dataParent.length()>0){
           for (String field:wizardFields.keySet()){
               WizardField wizardField = wizardFields.get(field);
               if (dataParent.equals(wizardField.getParent())){
                   peers = wizardField.getPeers();
               }
           }
       }
       List<String> possibleParents = new ArrayList<>();
       String keyFieldName = null;
        if (stage==PARENTSTAGE){

           for (String field:wizardFields.keySet()){
               WizardField wizardField = wizardFields.get(field);
               if (!KEYFIELDID.equals(wizardField.getType()) && !KEYFIELDNAME.equals(wizardField.getType()) && wizardField.getParent() ==null && wizardField.getAnchor()==null){
                   possibleParents.add(field);
               }
           }

       }

       try {
           if(chosenField!=null){
               ImportWizard.getDataValuesFromFile(loggedInUser.getWizardInfo(), chosenField, chosenValue);
           }



           List<ImportWizardField> toShow = new ArrayList<>();
            int count = 1;
            for (String field : wizardFields.keySet()) {

                WizardField wizardField = wizardFields.get(field);
                if (wizardField.getValueFound()==null && wizardField.getValuesFound()!=null&&wizardField.getValuesFound().size()>0){
                    getDataValuesFromFile(loggedInUser.getWizardInfo(),field,wizardField.getValuesFound().iterator().next());
                }
                if (stage==NAMESTAGE || wizardField.getSelect()) {
                    ImportWizardField importWizardField = new ImportWizardField(wizardField.getImportedName());
                    switch (stage) {
                        case NAMESTAGE:
                            importWizardField.setFieldName(wizardField.getImportedName());
                            importWizardField.setValuesFound(get100(wizardField.getValuesFound(), wizardField.getValueFound()));
                            importWizardField.setTextEntry(wizardField.getName());
                             importWizardField.setListEntry(null);
                            String checkstatus = "checked=true";
                            if (!wizardField.getSelect()) {
                                checkstatus = "";
                            }
                            importWizardField.setCheckEntry(checkBoxHTML(1,count,wizardField.getSelect(), false));
                            break;
                        case TYPESTAGE:
                            importWizardField.setFieldName(wizardField.getName());
                            importWizardField.setValuesFound(get100(wizardField.getValuesFound(), wizardField.getValueFound()));
                            importWizardField.setListEntry(new ArrayList<>());
                            importWizardField.getListEntry().add("");
                            for (String type : TYPEOPTIONS) {
                                if (type.equals(wizardField.getType())) {
                                    importWizardField.getListEntry().add(type + " selected");
                                } else {
                                    importWizardField.getListEntry().add(type);
                                }
                            }
                            break;
                        case DATASTAGE:
                            importWizardField.setFieldName(wizardField.getName());
                            importWizardField.setValuesFound(get100(wizardField.getValuesFound(), wizardField.getValueFound()));
                            if (isKeyField(wizardField)){
                                importWizardField.setCheckEntry(".");
                            }else{
                                importWizardField.setCheckEntry(checkBoxHTML(1,count,dataParent.equals(wizardField.getParent()), true));
                            }
                            if (KEYFIELDNAME.equals(wizardField.getType())|| wizardField.getParent()!=null){
                                importWizardField.setCheck2Entry(".");
                            }else{
                                importWizardField.setCheck2Entry(checkBoxHTML(2,count,peers.contains(field), false));
                            }
                            break;

                        case PARENTSTAGE:
                            List<String> possibleChildren = new ArrayList<>();
                            List<String> possibleAnchors = new ArrayList<>();
                            possibleChildren.add("");
                            possibleAnchors.add("");
                            importWizardField.setExample("");
                            importWizardField.setFieldName(wizardField.getName());

                            if (wizardField.getParent() != null){
                                importWizardField.setExample("Value in " + wizardField.getParent());
                                 importWizardField.setListEntry(possibleChildren);
                                importWizardField.setList2Entry(possibleAnchors);
                                break;
                            }
                            if (KEYFIELDID.equals(wizardField.getType())){
                                importWizardField.setExample(KEYFIELDID);
                                importWizardField.setListEntry(possibleChildren);
                                importWizardField.setList2Entry(possibleAnchors);
                                break;
                            }

                            String currentChild = null;
                            String childValue = null;
                            if (possibleParents.contains(field) && wizardField.getAnchor()==null) {
                                //check we have some sample values
                                 if (wizardField.getValueFound().length() == 0) {
                                    for (String value : wizardField.getValuesFound()) {
                                        if (value.length() > 0) {
                                            ImportWizard.getDataValuesFromFile(loggedInUser.getWizardInfo(), field, value);
                                            break;
                                        }
                                    }
                                }
                                if (wizardField.getChild() != null) {
                                    WizardField childField = wizardFields.get(wizardField.getChild());
                                    childValue = getChildValue(wizardFields, childField);
                                    importWizardField.setExample("e.g. " + wizardField.getValueFound() + " is parent of " + childValue);
                                    currentChild = wizardFields.get(wizardField.getChild()).getName();
                                }
                                for (String ch : wizardFields.keySet()) {
                                    WizardField child = wizardFields.get(ch);

                                    if (child.getName().equals(currentChild)) {
                                        possibleChildren.add(child.getName() + " selected");

                                    } else {
                                        possibleChildren.add(child.getName());
                                    }
                                }
                            }

                            String currentAnchor = null;

                            if (!KEYFIELDID.equals(wizardField) && wizardField.getChild() == null && wizardField.getParent() == null) {
                                if (wizardField.getAnchor() != null) {
                                    WizardField anchorField = wizardFields.get(wizardField.getAnchor());
                                    String anchorValue = getChildValue(wizardFields, anchorField);
                                    importWizardField.setExample("e.g. " + wizardField.getValueFound() + " is an attribute of " + anchorValue);
                                    currentAnchor = wizardFields.get(wizardField.getAnchor()).getName();
                                }
                                for (String an : wizardFields.keySet()) {
                                    WizardField anchor = wizardFields.get(an);
                                    if (KEYFIELDID.equals(anchor.getType()) || anchor.getChild() != null) {

                                        if (anchor.getName().equals(currentAnchor)) {
                                            possibleAnchors.add(anchor.getName() + " selected");

                                        } else {
                                            possibleAnchors.add(anchor.getName());
                                        }
                                    }
                                }
                            }
                            importWizardField.setListEntry(possibleChildren);
                            importWizardField.setList2Entry(possibleAnchors);
                    }
                    toReturn.add(importWizardField);
                    count++;
                }



            }
        }catch (Exception w){
            //TODO....
        }
        return toReturn;

    }

    private static String getChildValue(Map<String, WizardField> wizardFields, WizardField childField){
        String childValue = childField.getValueFound();
        if (KEYFIELDID.equals(childField.getType())) {
            for (String nameField : wizardFields.keySet()) {
                WizardField nameF = wizardFields.get(nameField);
                if (KEYFIELDNAME.equals(nameF.getType())) {
                    childValue += " (" + nameF.getValueFound() + ")";
                }
            }
        }
        return childValue;

    }

    private static String checkBoxHTML(int checkColumn, int fieldNo, boolean checked, boolean hasOnChange){
        String checkstatus = "checked=true";
        if (!checked) {
            checkstatus = "";
        }

        if (hasOnChange){
            checkstatus += " onChange=\"checkChanged(" + fieldNo + ")\"";
        }
        return "<input type=\"checkbox\" id=\"check"+ checkColumn + "-" + fieldNo +"\" " + checkstatus + "\">";

    }

    private static List<String> get100(List<String> source, String selected){
        List<String> target = new ArrayList<>();
        int count = source.size();
        count = 0;
        for (String element:source){
            if (element.equals(selected)){
                target.add(element + " selected");
            }else{
                target.add(element);
            }
            if (++count == 100){
                break;
            }
        }
        return target;

    }

    public static String handleRequest(LoggedInUser loggedInUser, HttpServletRequest request){

        WizardInfo wizardInfo = loggedInUser.getWizardInfo();
        final ObjectMapper jacksonMapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        jacksonMapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
        String chosenField = request.getParameter("chosenfield");
        String chosenValue = request.getParameter("chosenvalue");
        int stage = Integer.parseInt(request.getParameter("stage"));
        int nextStage = Integer.parseInt(request.getParameter("nextstage"));
        String dataParent = request.getParameter("dataparent");
        String fieldsInfo = request.getParameter("fields");
        String error = processFound(wizardInfo,fieldsInfo,stage, dataParent);
        if (error!=null){
            return "error:" + error;
        }
        stage = nextStage;
        try {
            Map<String, List<ImportStage>> output = new HashMap<>();
            List<ImportStage> stages = createStageList(nextStage);
            if (nextStage > wizardInfo.getMaxStageReached()) {
                String suggestion = ImportWizard.makeSuggestion(loggedInUser.getWizardInfo(), nextStage);
                if (suggestion.length() > 1) {
                    stages.get(stage - 1).setSuggestions("<p>" + suggestion + "</p><p>You can override these suggestions:</p>");
                }

                wizardInfo.setMaxStageReached(stage);
            }
             if (nextStage == DATASTAGE) {
                if (dataParent == null || dataParent.length() == 0) {
                    dataParent = "";
                    for (String field : wizardInfo.getFields().keySet()) {
                        WizardField wizardField = wizardInfo.getFields().get(field);
                        if (wizardField.getParent() != null) {
                            dataParent = wizardField.getParent();
                            break;
                        }
                    }
                }
                stages.get(nextStage - 1).setDataParent(dataParent);
            }

            output.put("stage", stages);
            String toReturn = jacksonMapper.writeValueAsString(output);

            List<ImportWizardField> wizardFields = createWizardFieldList(loggedInUser, nextStage, chosenField, chosenValue, dataParent);
            Map<String,List<ImportWizardField>> fields = new HashMap<>();
            fields.put("field", wizardFields);
            String fieldInfo = jacksonMapper.writeValueAsString(fields);
            toReturn += fieldInfo;
            return toReturn.replace("}{",",");//not proud of this!



        }catch(Exception e){
            return "error:" + e.getMessage();
        }
    }

    public static String uploadTheFile(LoggedInUser loggedInUser, UploadedFile uploadFile){
        try {
            WizardInfo wizardInfo;
            String fileName = uploadFile.getFileName();
            if (fileName.toLowerCase(Locale.ROOT).contains(".xls")) {
                Map<String, WizardField> wizardFields = new LinkedHashMap<>();
                int lineCount = ImportWizard.readBook(new File(uploadFile.getPath()), wizardFields, false);
                wizardInfo = new WizardInfo(fileName, null);
                wizardInfo.setFields(wizardFields);
                wizardInfo.setLineCount(lineCount);
                loggedInUser.setWizardInfo(wizardInfo);

            } else if (fileName.toLowerCase(Locale.ROOT).contains(".json") || fileName.toLowerCase(Locale.ROOT).contains(".xml")) {
                String data = new String(Files.readAllBytes(Paths.get(uploadFile.getPath())), Charset.defaultCharset());
                JSONObject jsonObject = null;
                if (uploadFile.getPath().toLowerCase(Locale.ROOT).endsWith(".xml")) {
                    jsonObject = XML.toJSONObject(data);
                } else {
                    if (data.charAt(0) == '[') {
                        data = "{data:" + data + "}";
                    }
                    data = data.replace("\n", "");//remove line feeds
                    jsonObject = new JSONObject(data);
                }

                wizardInfo = new WizardInfo(uploadFile.getFileName(), jsonObject);
                loggedInUser.setWizardInfo(wizardInfo);
            } else {
                Map<String, String> fileNameParameters = new HashMap<>();
                ImportService.addFileNameParametersToMap(uploadFile.getFileName(), fileNameParameters);

                Map<String, WizardField> wizardFields = ImportWizard.readCSVFile(uploadFile.getPath(), fileNameParameters.get("fileencoding"));
                wizardInfo = new WizardInfo(fileName, null);
                wizardInfo.setFields(wizardFields);
                for (String field : wizardFields.keySet()) {
                    WizardField wizardField = wizardFields.get(field);
                    wizardInfo.setLineCount(wizardField.getValuesFound().size());
                    break;
                }

                loggedInUser.setWizardInfo(wizardInfo);
            }
        }catch (Exception e){
            //todo
            int k=1;
        }
        return "importwizard";

    }

    /*


    public static void handleZip(UploadedFile uploadedFile, HTTPSession session)throws Exception{
        if (uploadedFile.getFileName().toLowerCase().endsWith(".zip") || uploadedFile.getFileName().toLowerCase().endsWith(".7z")) {
            try {
                ZipUtil.explode(new File(uploadedFile.getPath()));
            } catch (ZipException ze) { // try for decrypt
            }
            // after exploding the original file is replaced with a directory
            File zipDir = new File(uploadedFile.getPath());
            zipDir.deleteOnExit();
            // todo - go to Files.list()?
            List<File> files = new ArrayList<>(FileUtils.listFiles(zipDir, null, true));
            // should be sorting by xls first then size ascending
           Iterator<File> fileIterator = files.iterator();

            for (File f : files) {
                     // need new upload file object now!
                    List<String> names = new ArrayList<>(uploadedFile.getFileNames());
                    names.add(f.getName());
                    Map<String, String> fileNameParams = new HashMap<>(uploadedFile.getParameters());
                    addFileNameParametersToMap(f.getName(), fileNameParams);
                    // bit hacky to stop the loading but otherwise there'd just be another map
                    if (pendingUploadConfig != null && pendingUploadConfig.getParametersForFile(f.getName()) != null) {
                        Map<String, String> parametersForFile = pendingUploadConfig.getParametersForFile(f.getName());
                        // don't change this to entries - the keys are converted to lower case
                        for (String key : parametersForFile.keySet()) {
                            fileNameParams.put(key.toLowerCase(), parametersForFile.get(key));
                        }
                    }
                    UploadedFile zipEntryUploadFile = new UploadedFile(f.getPath(), names, fileNameParams, false, uploadedFile.isValidationTest());
                    processedUploadedFiles.addAll(checkForCompressionAndImport(loggedInUser, zipEntryUploadFile, session, pendingUploadConfig, templateCache, userComment));
                }
            }

        }

     */


}
