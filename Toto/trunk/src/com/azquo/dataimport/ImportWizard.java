package com.azquo.dataimport;

import com.azquo.StringLiterals;
import com.azquo.admin.business.Business;
import com.azquo.spreadsheet.LoggedInUser;
import com.azquo.spreadsheet.transport.UploadedFile;
import com.azquo.spreadsheet.zk.BookUtils;
import io.keikai.api.Importers;
import io.keikai.api.model.Book;
import io.keikai.api.model.Sheet;
import io.keikai.model.SName;
import org.apache.poi.ss.formula.functions.Value;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.AreaReference;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.jfree.data.Values;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.ui.ModelMap;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Pattern;

public class ImportWizard {

    public static class ValueFound {
        String heading;
        List<String> values;

        ValueFound(String heading, List<String> values) {
            this.heading = heading;
            this.values = values;
        }

        public String getHeading() { return heading; }

        public List<String> getValues(){return values;   }
    }

    private static int nthLastIndexOf(int nth, String ch, String string) {
        if (nth <= 0) return string.length();
        if (string.lastIndexOf(ch)==-1) return -1;//start of string
        return nthLastIndexOf(--nth, ch, string.substring(0, string.lastIndexOf(ch)));
    }


    public static Map<String,WizardField> readTheFile(WizardInfo wizardInfo, String testItem)  {
        Map<String,List<String>> fieldsFound = new LinkedHashMap<>();
        try {
            JSONObject jsonObject = new JSONObject(wizardInfo.getImportFileData());//remove line feeds
            traverseJSON(fieldsFound, "", jsonObject,10, ImportService.JSONFIELDDIVIDER + testItem);
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
                    wizardInfo.getFields().put(field,new WizardField(null,fieldsFound.get(field),null));
                    wizardInfo.getFields().get(field).setName(suggestedName);
                }
            }else{
                for (String field:fieldsFound.keySet()){
                      wizardInfo.getFields().get(field).setValuesFound(fieldsFound.get(field));
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

    private static boolean traverseJSON(Map<String,List<String>>fieldsFound, String jsonPath,JSONObject jsonNext, int maxArraySize, String testItem) throws Exception{
        boolean found = false;
        String[] jsonNames = JSONObject.getNames(jsonNext);
        for (String jsonName:jsonNames){
            String newPath = jsonPath + ImportService.JSONFIELDDIVIDER + jsonName;
            if (testItem.length()>1 && fieldsFound.get(newPath.substring(1))!=null){
                fieldsFound.get(newPath.substring(1)).clear();
            }
            if(testItem.length()==1 || testItem.startsWith(jsonPath)) {
                try {
                    JSONObject jsonObject = jsonNext.getJSONObject(jsonName);
                    if(traverseJSON(fieldsFound, newPath, jsonObject, maxArraySize, testItem)){
                        found = true;
                    };
                } catch (Exception e) {
                    try {

                        JSONArray jsonArray = jsonNext.getJSONArray(jsonName);
                        int count = 0;
                        for (Object jsonObject1 : jsonArray) {
                            if (found) break;
                            if(traverseJSON(fieldsFound, newPath, (JSONObject) jsonObject1, maxArraySize, testItem)){
                                found = true;
                            };
                            if (count++ == maxArraySize) {
                                break;
                            }

                        }
                    } catch (Exception e2) {
                        String value = jsonNext.get(jsonName).toString();
                        if (testItem.equals(newPath+ImportService.JSONFIELDDIVIDER + value) || testItem.equals(newPath+ImportService.JSONFIELDDIVIDER + "*")){
                            found = true;
                        }
                        List<String>valuesFound = fieldsFound.get(newPath.substring(1));
                        if (valuesFound==null){
                            valuesFound = new ArrayList<>();
                            fieldsFound.put(newPath.substring(1), valuesFound);
                        }
                        fieldsFound.get(newPath.substring(1)).add(value);
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
            //import the names first - peers are described by the chosen names rather than the import file names.
            for (String field : wizardInfo.getFields().keySet()) {
                WizardField wizardField = wizardInfo.getFields().get(field);
                String loadedField = ImportService.getCellValue(importSheet, row, col);
                if (!field.equals(loadedField)) {
                    return "Expected " + field + " - found " + loadedField;
                }
                wizardField.setName(ImportService.getCellValue(importSheet, row, col + 1));
                row++;
            }
            row = startRow;
            for (String field : wizardInfo.getFields().keySet()) {
                WizardField wizardField = wizardInfo.getFields().get(field);
                String loadedField = ImportService.getCellValue(importSheet,row,col);
                if (ImportService.getCellValue(importSheet,row, col + 2).length()>0)
                    wizardField.setIgnore(true);
                String interpetation = ImportService.getCellValue(importSheet,row,col + 3);
                String[] clauses = interpetation.split(";");
                wizardField.setType(clauses[0]);
                String dataParent = getClause("datatype", clauses);
                if (dataParent==null && clauses[0].equals("data")){
                    wizardField.setType(null);
                }
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
        if ("null".equals(wizardField.getType()) || "".equals(wizardField.getType())) {
            wizardField.setType(null);

        }

        String peerType = null;
        if (peerHeadings.size() > 0){
            peerType ="peer for " + peerHeadings.get(0);
            for (int peer = 1;peer<peerHeadings.size();peer++){
                peerType +="," +peerHeadings.get(peer);
            }
        }
        String type = wizardField.getType();

        if (wizardField.getChild()!=null){
            type ="parent  of " + wizardInfo.getFields().get(wizardField.getChild()).getName();
        }
        if (wizardField.getAnchor()!=null){
            type = "attribute of " + wizardInfo.getFields().get(wizardField.getAnchor()).getName();
        }

        if (peerType!=null && type==null){
            type=peerType;
            peerType = null;

        }



        if (type==null ) {
            wizardField.setInterpretation("");
            return;
        }

        toReturn.append(";"+type);
        if (wizardField.getParent()!=null){
            toReturn.append(";datatype " + wizardField.getParent());
        }
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
            wizardField.setInterpretation("");
        }
    }

    private static String findFieldFromName(WizardInfo wizardInfo, String fieldName){
        for (String field:wizardInfo.getFields().keySet()){
            if (wizardInfo.getFields().get(field).getName().equals(fieldName.trim())){
                return field;
            }
        }
        return null;
    }

    public static void createDB(LoggedInUser loggedInUser) throws Exception{
        WizardInfo wizardInfo = loggedInUser.getWizardInfo();
        String dataField = null;
        Map<String, List<String>> pathsRequired = new LinkedHashMap<>();
        Map<String,List<String>> flatfileData = new LinkedHashMap<>();
        for (String field:wizardInfo.getFields().keySet()){
            WizardField wizardField = wizardInfo.getFields().get(field);
            if (!wizardField.getIgnore() && wizardField.getInterpretation().length() > 0) {
               if (wizardInfo.getFields().get(field).getParent() != null) {
                    dataField = field;
               }
               pathsRequired.put(field, new ArrayList<>());
               flatfileData.put(field,new ArrayList<>());
            }
        }
        if (dataField==null){
            throw new Exception("no data fields");
        }
         traverseJSON3(flatfileData,pathsRequired,"", new JSONObject(wizardInfo.getImportFileData()));
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


    private static boolean traverseJSON3(Map<String,List<String>> output, Map<String,List<String>> pathsRequired, String jsonPath,JSONObject jsonNext) throws Exception{
        boolean found = false;
        String[] jsonNames = JSONObject.getNames(jsonNext);
        for (String jsonName:jsonNames){
            String newPath = jsonPath + ImportService.JSONFIELDDIVIDER + jsonName;
            if(inRequired(newPath.substring(1), pathsRequired)) {
                try {
                    JSONObject jsonObject = jsonNext.getJSONObject(jsonName);
                    found = traverseJSON3(output, pathsRequired, newPath, jsonObject);
                } catch (Exception e) {
                    try {

                        JSONArray jsonArray = jsonNext.getJSONArray(jsonName);
                    } catch (Exception e2) {
                        String value = jsonNext.get(jsonName).toString();
                        if (pathsRequired.get(newPath.substring(1))!=null){
                            pathsRequired.get(newPath.substring(1)).add(value);
                            found = true;
                        }
                    }
                }
            }
        }
        for (String jsonName:jsonNames){
            String newPath = jsonPath + ImportService.JSONFIELDDIVIDER + jsonName;
            if(inRequired(newPath.substring(1), pathsRequired)) {
                try {
                    JSONObject jsonObject = jsonNext.getJSONObject(jsonName);
                } catch (Exception e) {
                    try {

                        JSONArray jsonArray = jsonNext.getJSONArray(jsonName);
                        int count = 0;
                        for (Object jsonObject1 : jsonArray) {
                             found = traverseJSON3(output, pathsRequired, newPath, (JSONObject) jsonObject1);
                        }
                        if (found){
                            outputAdd(output,pathsRequired);
                            found = false;
                        }
                    } catch (Exception e2) {
                    }
                }
            }
        }
        return found;
    }

    private static boolean inRequired(String toTest, Map<String, List<String>> required){
        for (String maybe:required.keySet()){
            if (maybe.startsWith(toTest)){
                return true;
            }
        }
        return false;
    }

    private static void outputAdd(Map<String,List<String>> output, Map<String,List<String>> found){
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
                for (int i = found.get(field).size();i < maxCount;i++){
                    output.get(field).add(found.get(field).get(0));
                }
            }else{
                found.get(field).clear();
            }
        }
    }

}
