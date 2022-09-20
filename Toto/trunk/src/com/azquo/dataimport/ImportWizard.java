package com.azquo.dataimport;

import com.azquo.DateUtils;
import com.azquo.StringLiterals;
import com.azquo.admin.database.DatabaseDAO;
import com.azquo.memorydb.service.NameService;
import com.azquo.rmi.RMIClient;
import com.azquo.spreadsheet.CommonReportUtils;
import com.azquo.spreadsheet.LoggedInUser;
import com.azquo.spreadsheet.SpreadsheetService;
import com.azquo.spreadsheet.controller.OnlineController;
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
import io.keikai.api.Books;
import io.keikai.api.Importers;
import io.keikai.api.Ranges;
import io.keikai.api.model.Book;
import io.keikai.model.SName;
import org.apache.commons.lang.math.NumberUtils;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.AreaReference;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.XML;
import org.springframework.beans.factory.annotation.Autowired;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.apache.poi.ss.usermodel.CellType.NUMERIC;
import static org.apache.poi.ss.usermodel.CellType.STRING;


public class ImportWizard {

    @Autowired
    static ServletContext servletContext;


    public static final String WIZARDFILENAME = "AzquoImportWizard";

 

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

     // fragment to add our function(s) -  NEEDS WORK!
    //String[] functionNames = {"ImportUtils.standardise"};
    //FreeRefFunction[] functionImpls = {new POIImportUtils.standardise()};
    //UDFFinder udfs = new DefaultUDFFinder(functionNames, functionImpls);
    //UDFFinder udfToolpack = new AggregatingUDFFinder(udfs);

    //wb.addToolPack(udfToolpack);

    public static final String KEYFIELDID = "key field id";
    public static final String KEYFIELDNAME = "key field name";
    public static final int MATCHSTAGE = 0;
    public static final int NAMESTAGE = 1;
    public static final int TYPESTAGE = 2;
    public static final int DATASTAGE = 3;
    public static final int PARENTSTAGE = 4;
    public static final int SPECIALSTAGE = 5;
    public static final int EXCELSTAGE = 7;


 
     public static String reloadWizardInfo(LoggedInUser loggedInUser, org.apache.poi.ss.usermodel.Workbook book) {
        try {
            org.apache.poi.ss.usermodel.Sheet importSheet = book.getSheetAt(0);
            WizardInfo wizardInfo = loggedInUser.getWizardInfo();
            org.apache.poi.ss.usermodel.Name headingRegion = BookUtils.getName(book, "az_Headings");
            AreaReference areaReference = new AreaReference(headingRegion.getRefersToFormula(), null);

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
            for (String field : addedFields) {
                wizardInfo.getFields().remove(field);
            }

            //import the names first - peers are described by the chosen names rather than the import file names.

            while (ImportService.getCellValue(importSheet, row, col).length() > 0) {
                String loadedField = ImportService.getCellValue(importSheet, row, col);
                String azquoName = ImportService.getCellValue(importSheet, row, col + 1);
                WizardField wizardField = wizardInfo.getFields().get(ImportUtils.HTMLify(loadedField));
                if (wizardField == null) {
                    wizardField = new WizardField(loadedField, ImportUtils.insertSpaces(azquoName), true);
                    wizardInfo.getFields().put(ImportUtils.HTMLify(loadedField), wizardField);
                } else {
                    wizardField.setName(azquoName);
                }
                wizardField.setSelect(true);
                row++;

            }
            row = startRow;
            while (ImportService.getCellValue(importSheet, row, col).length() > 0) {
                String loadedField = ImportService.getCellValue(importSheet, row, col);
                WizardField wizardField = wizardInfo.getFields().get(ImportUtils.HTMLify(loadedField));
                String interpetation = ImportService.getCellValue(importSheet, row, col + 3);
                String[] clauses = interpetation.split(";");
                if (Arrays.asList(ImportUtils.TYPEOPTIONS).contains(clauses[0])) {
                    wizardField.setType(clauses[0]);

                }
                String dataChild = getClause("parent of", clauses);
                if (dataChild != null) {
                    wizardField.setChild(ImportUtils.findFieldFromName(wizardInfo, dataChild));
                }
                String dataAnchor = getClause("attribute of", clauses);
                if (dataAnchor != null) {
                    wizardField.setAnchor(ImportUtils.findFieldFromName(wizardInfo, dataAnchor));
                }
                String dataParent = getClause("datagroup", clauses);
                wizardField.setParent(dataParent);
                String peersString = getClause("peers", clauses);
                if (peersString != null) {
                    setPeers(wizardInfo, wizardField, peersString);
                }
                setInterpretation(wizardInfo, wizardField);
                row++;
                // todo don't show admins if not admin
            }
            //repass interpretations to get all 'peer for' instances
            for (String field : wizardInfo.getFields().keySet()) {
                setInterpretation(wizardInfo, wizardInfo.getFields().get(field));
            }

            ImportUtils.calcXL(loggedInUser.getWizardInfo());
            return "";
        } catch (Exception e) {
            return e.getMessage();
        }


    }

    private static void setPeers(WizardInfo wizardInfo, WizardField wizardField, String peersString) {
        peersString = peersString.trim();
        peersString = peersString.substring(1, peersString.length() - 1);//strip{}
        String[] peers = peersString.split(",");
        List<String> dataPeers = new ArrayList<>();
        for (String peer : peers) {
            String dataField = ImportUtils.findFieldFromName(wizardInfo, peer);
            if (dataField != null) {
                dataPeers.add(dataField);

            }
        }
        if (dataPeers.size() > 0) {
            wizardField.setPeers(dataPeers);
        } else {
            wizardField.setPeers(null);
        }

    }

    private static String getClause(String toFind, String[] array) {
        for (String element : array) {
            if (element.startsWith(toFind)) {
                return element.substring(toFind.length()).trim();
            }
        }
        return null;
    }

    private static boolean usedName(WizardInfo wizardInfo, String name) {
        for (String field : wizardInfo.getFields().keySet()) {
            if (wizardInfo.getFields().get(field).getName().equalsIgnoreCase(name)) {
                return true;
            }
        }
        if (name.equals(wizardInfo.getLastDataField())) {
            return true;
        }
        return false;

    }

    public static String processFound(WizardInfo wizardInfo, String fields, int stage, String dataParent) {
        if (fields.length() == 0) {
            return null;
        }
        Set<String> potentialPeers = new HashSet<>();

        String keyField = null;
        for (String field : wizardInfo.getFields().keySet()) {
            WizardField wizardField = wizardInfo.getFields().get(field);
            if (KEYFIELDID.equals(wizardField.getType())) {
                keyField = field;
            }
            if (wizardField.getPeers() != null) {
                potentialPeers.addAll(wizardField.getPeers());
            }
        }
        String[] fieldLines = fields.split("\n");
        switch (stage) {
              case MATCHSTAGE:
                  for (String line : fieldLines) {
                      String[] elements = line.split("\t");

                      if (elements[0].length() > 0) {
                          WizardField wizardField = wizardInfo.getFields().get(ImportUtils.findFieldFromName(wizardInfo,elements[0]));
                          matchTheField(wizardInfo, wizardField, null);;
                          //field name, values, matched name
                          if (elements.length> 2 && elements[2].length()>0) {
                              matchTheField(wizardInfo, wizardField, elements[2]);
                              wizardField.setMatchedFieldName(elements[2]);
                          }
                      }
                  }
                  break;
            case NAMESTAGE:
                List<String> toDelete = new ArrayList<>();
                for (String line : fieldLines) {
                    String[] elements = line.split("\t");
                    if (elements[0].length() > 0) {
                        WizardField wizardField = wizardInfo.getFields().get(ImportUtils.standardise(elements[0]));
                        if (wizardField == null) {
                            wizardField = new WizardField(elements[0], elements[2], true);
                            wizardInfo.getFields().put(elements[0], wizardField);
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
                for (String field : toDelete) {
                    wizardInfo.getFields().remove(field);
                }
                break;
            case TYPESTAGE:
                for (String line : fieldLines) {
                    String[] elements = line.split("\t");
                    WizardField wizardField = wizardInfo.getFields().get(ImportUtils.findFieldFromName(wizardInfo, elements[0]));
                    //imported name, values, name, selected
                    if (elements.length < 3) {
                        wizardField.setType(null);
                    } else {
                        wizardField.setType(elements[2]);
                    }
                }
                break;

            case DATASTAGE:
                if (dataParent == null) {
                    return null;
                }
                List<String> peers = new ArrayList<>();
                String peerParent = dataParent;
                if (wizardInfo.getLastDataField() != null) {
                    peerParent = wizardInfo.lastDataField;
                }

                for (String field : wizardInfo.getFields().keySet()) {
                    WizardField wizardField = wizardInfo.getFields().get(field);
                    if (peerParent.equals(wizardField.getParent())) {
                        peers = wizardField.getPeers();
                        break;
                    }
                }
                if (wizardInfo.getLastDataField() != null) {
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
                for (String line : fieldLines) {
                    String[] fieldInfo = line.split("\t");
                    WizardField wizardField = wizardInfo.getFields().get(ImportUtils.findFieldFromName(wizardInfo, fieldInfo[0]));
                    if ("true".equals(fieldInfo[2])) {
                          wizardField.setParent(dataParent);
                        wizardField.setPeers(peers);
                    }else{
                        wizardField.setParent(null);
                        wizardField.setPeers(null);
                    }

                }
                peers = new ArrayList<>();
                //peers
                //fieldName, valuesFound, checkEntry"
                for (String line : fieldLines) {
                    String[] fieldInfo = line.split("\t");
                    if ("true".equals(fieldInfo[3])) {
                        peers.add(ImportUtils.findFieldFromName(wizardInfo, fieldInfo[0]));
                    }

                }
                for (String field : wizardInfo.getFields().keySet()) {
                    WizardField wizardField = wizardInfo.getFields().get(field);
                    if (dataParent.equals(wizardField.getParent())) {
                        wizardField.setPeers(peers);
                    }
                }
                break;
            case PARENTSTAGE:
                for (String line : fieldLines) {

                    String[] fieldInfo = line.split("\t");
                    String newChild = "";
                    String newAnchor = "";
                    try {
                        newChild = fieldInfo[1];
                        newAnchor = fieldInfo[2];
                    } catch (Exception e) {
                        //blanks
                    }
                    String field = ImportUtils.findFieldFromName(wizardInfo, fieldInfo[0]);
                    WizardField wizardField = wizardInfo.getFields().get(ImportUtils.findFieldFromName(wizardInfo, fieldInfo[0]));
                    //no adjustments to data or key fields, and peer fields cannot be attributes...
                    if (wizardField.getParent() == null && !ImportUtils.isKeyField(wizardField) && (!potentialPeers.contains(field) || newAnchor.length() == 0)) {
                        if (newChild.length() > 0) {
                            wizardField.setChild(ImportUtils.findFieldFromName(wizardInfo, newChild));
                            wizardField.setAnchor(null);

                        } else if (newAnchor.length() > 0) {
                            wizardField.setAnchor(ImportUtils.findFieldFromName(wizardInfo, newAnchor));
                            wizardField.setChild(null);

                        } else if (wizardField.getChild() != null) {
                            wizardField.setChild(null);
                            wizardField.setAnchor(keyField);
                            for (String field2 : wizardInfo.getFields().keySet()) {
                                WizardField wizardField2 = wizardInfo.getFields().get(field2);
                                if (field.equals(wizardField2.getAnchor())) {
                                    wizardField2.setAnchor(keyField);
                                }
                            }
                            break;
                        } else if (wizardField.getAnchor() != null) {
                            wizardField.setAnchor(null);
                            wizardField.setChild(keyField);
                        }
                    }
                }
                break;
            case SPECIALSTAGE:
                for (String line : fieldLines) {
                    String[] fieldInfo = line.split("\t");
                    WizardField wizardField = wizardInfo.getFields().get(ImportUtils.findFieldFromName(wizardInfo, fieldInfo[0]));
                    if (fieldInfo.length > 1) {
                        wizardField.setSpecialInstructions(fieldInfo[1]);
                    } else {
                        wizardField.setSpecialInstructions("");
                    }
                }
                try{
                    ImportUtils.calcXL(wizardInfo);
                }catch (Exception e){
                    //TODO
                    int k=1;
                }
        }
        for (String heading : wizardInfo.getFields().keySet()) {
            WizardField wizardField = wizardInfo.getFields().get(heading);
            setInterpretation(wizardInfo, wizardField);
        }

        return null;
    }

  
    public static void setInterpretation(WizardInfo wizardInfo, WizardField wizardField) {

        String field = ImportUtils.findFieldFromName(wizardInfo, wizardField.getName());
        List<String> peerHeadings = new ArrayList<>();
        for (String heading : wizardInfo.getFields().keySet()) {
            WizardField peerField = wizardInfo.getFields().get(heading);
            if (peerField.getPeers() != null && peerField.getPeers().contains(field)) {
                if (!peerHeadings.contains(peerField.getParent())) {
                    peerHeadings.add(peerField.getParent());
                }
            }
        }


        StringBuffer toReturn = new StringBuffer();

        String type = wizardField.getType();
        if (type != null) {
            toReturn.append(";" + type);
        }
        if (!wizardField.getSelect()) {
            toReturn.append(";ignore");
        }
        String peerType = null;
        if (peerHeadings.size() > 0) {
            peerType = "peer for " + peerHeadings.get(0);
            for (int peer = 1; peer < peerHeadings.size(); peer++) {
                peerType += "," + peerHeadings.get(peer);
            }
            toReturn.append(";" + peerType);
        }

        if (wizardField.getChild() != null) {
            toReturn.append(";parent of " + wizardInfo.getFields().get(wizardField.getChild()).getName());
        }
        if (wizardField.getAnchor() != null) {
            toReturn.append(";attribute of " + wizardInfo.getFields().get(wizardField.getAnchor()).getName());
        }

        if (wizardField.getParent() != null) {
            toReturn.append(";datagroup " + wizardField.getParent());
        }

        if (ImportUtils.DATELANG.equals(wizardField.getType())) {
            toReturn.append(";datatype date");
        }
        if (wizardField.getAdded()) {
            toReturn.append(";added");
        }


        if (wizardField.getPeers() != null && wizardField.getPeers().size() > 0) {
            //this is rather a tiresome routine to mark the peers with 'peer for <data parent name>' - we don't need this info in Azquo, but it's helpful to the user
            toReturn.append(";peers {");
            boolean firstPeer = true;

            for (String peer : wizardField.getPeers()) {
                WizardField wizardPeer = wizardInfo.getFields().get(peer);
                if (!firstPeer) toReturn.append(",");
                toReturn.append(ImportUtils.removeId(peer));
                firstPeer = false;
            }
            toReturn.append("}");
        }
        if (wizardField.getSpecialInstructions().length() > 0) {
            toReturn.append(";" + wizardField.getSpecialInstructions());
        }
        if (toReturn.length() == 0) {
            wizardField.setInterpretation(checkForParents(wizardInfo, wizardField));
            return;
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

    public static void createDB(LoggedInUser loggedInUser) throws Exception {
        WizardInfo wizardInfo = loggedInUser.getWizardInfo();
        String dataField = null;
        Set<String> pathsRequired = new HashSet<>();
        Map<String, List<String>> flatfileData = new LinkedHashMap<>();
        Map<String, List<String>> onePassValues = new HashMap<>();
        boolean isJSON = true;
        for (String field : wizardInfo.getFields().keySet()) {
            WizardField wizardField = wizardInfo.getFields().get(field);
            if (wizardField.getMatchedFieldName()!=null && !wizardField.getMatchedFieldName().equals(field)){
                wizardInfo.getLookups().put(wizardField.getMatchedFieldName(), field);
            }
            setInterpretation(wizardInfo, wizardField);
            if (wizardField.getSelect() && wizardField.getInterpretation().length() > 0) {
                if (!field.contains(" where ")) {
                    pathsRequired.add(ImportService.JSONFIELDDIVIDER + field);
                }
                if (wizardInfo.getImportFileData() == null) {
                    flatfileData.put(field, wizardField.getValuesFound());
                    isJSON = false;
                } else {
                    flatfileData.put(field, new ArrayList<>());
                    onePassValues.put(field, new ArrayList<>());
                }
            }
        }
        if (isJSON) {
            List<JSONtest> jsonTests = new ArrayList<>();
            String error = ImportUtils.createJSONTests(wizardInfo, jsonTests, null, null);
            for (JSONtest jsoNtest : jsonTests) {
                pathsRequired.add(jsoNtest.source);
            }
            try {

                traverseJSON3(flatfileData, pathsRequired, onePassValues, "", wizardInfo.getImportFileData(), jsonTests, null);
            } catch (Exception e) {
                throw e;
            }
        }
        String keyField = null;
        int fieldCount = 0;
        while (fieldCount < wizardInfo.getFields().size()) {//Not using standard loop as the set of wizardFields may expand.

            List<String> fields = new ArrayList<>();
            for (String field : wizardInfo.getFields().keySet()) {
                fields.add(field);
            }
            String field = fields.get(fieldCount++);
            WizardField wizardField = wizardInfo.getFields().get(field);
            if (KEYFIELDID.equals(wizardField.getType())) {
                keyField = field;
            }

            if (KEYFIELDNAME.equals(wizardField.getType())) {
                if (wizardField.getAnchor() != null && !wizardField.getAnchor().equals(keyField)) {
                    //create new field so that this field can be the attribute of two others
                    WizardField wizardField1 = new WizardField(wizardField.getImportedName() + "-copy", wizardField.getName(), true);
                    wizardField1.setAnchor(wizardField.getAnchor());
                    wizardInfo.getFields().put(wizardField.getImportedName() + "-copy", wizardField1);
                    setInterpretation(wizardInfo, wizardField1);
                    flatfileData.put(wizardField1.getImportedName(), wizardField.getValuesFound());
                }
                wizardField.setAnchor(keyField);
                setInterpretation(wizardInfo, wizardField);


            }
            if (ImportUtils.DATELANG.equals(wizardField.getType()) || ImportUtils.USDATELANG.equals(wizardField.getType())) {
                flatfileData.put(field, ImportUtils.adjustDates(flatfileData.get(field), wizardField.getType()));
            }
            if ("time".equals(wizardField.getType())) {
                flatfileData.put(field, ImportUtils.adjustTImes(flatfileData.get(field)));
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
        uploadData(loggedInUser, flatfileData);

    }


    private static boolean traverseJSON3(Map<String, List<String>> output, Set<String> pathsRequired, Map<String, List<String>> onePassValues, String jsonPath, JSONObject jsonNext, List<JSONtest> jsonTests, String extractLevel) throws Exception {


        boolean tested = false;
        for (JSONtest jsoNtest : jsonTests) {
            if (jsoNtest.source.equals(jsonPath)) {
                try {
                    tested = true;
                    String jsonOtherValue = jsonNext.getString(jsoNtest.jsonPartner);
                    if (jsonOtherValue.equals(jsoNtest.jsonValue)) {
                        String jsonValue = jsonNext.getString(jsoNtest.jsonField);
                        if (jsoNtest.testValue == null || jsoNtest.testValue.equals(jsonValue)) {
                            onePassValues.get(jsoNtest.target).add(jsonValue);
                        }
                    }
                } catch (Exception e) {
                    //should we return an error?
                }
            }
        }
        if (tested) return true;

        String[] jsonNames = JSONObject.getNames(jsonNext);
        for (String jsonName : jsonNames) {
            String newPath = jsonPath + ImportService.JSONFIELDDIVIDER + jsonName;
            if (inRequired(newPath, pathsRequired)) {
                try {
                    JSONObject jsonObject = jsonNext.getJSONObject(jsonName);
                    traverseJSON3(output, pathsRequired, onePassValues, newPath, jsonObject, jsonTests, extractLevel);
                } catch (Exception e) {
                    try {

                        JSONArray jsonArray = jsonNext.getJSONArray(jsonName);
                    } catch (Exception e2) {
                        String value = jsonNext.get(jsonName).toString();
                        if (onePassValues.get(newPath.substring(1)) != null) {
                            onePassValues.get(newPath.substring(1)).add(value);
                        }
                    }
                }
            }
        }
        for (String jsonName : jsonNames) {
            String newPath = jsonPath + ImportService.JSONFIELDDIVIDER + jsonName;
            if (inRequired(newPath, pathsRequired)) {
                try {
                    JSONObject jsonObject = jsonNext.getJSONObject(jsonName);
                } catch (Exception e) {
                    try {

                        JSONArray jsonArray = jsonNext.getJSONArray(jsonName);
                        int count = 0;
                        if (extractLevel == null) {
                            extractLevel = newPath;
                        }
                        for (Object jsonObject1 : jsonArray) {
                            traverseJSON3(output, pathsRequired, onePassValues, newPath, (JSONObject) jsonObject1, jsonTests, extractLevel);
                            if (extractLevel.equals(newPath)) {
                                outputAdd(output, onePassValues, newPath.substring(1));
                            }
                        }
                    } catch (Exception e2) {
                    }
                }
            }
        }
        return true;
    }


    private static boolean inRequired(String toTest, Set<String> required) {
        for (String maybe : required) {
            if (maybe.startsWith(toTest)) {
                return true;
            }
        }
        return false;
    }

    private static void outputAdd(Map<String, List<String>> output, Map<String, List<String>> found, String extractLevel) {
        int maxCount = 0;
        for (String field : found.keySet()) {
            int count = found.get(field).size();
            if (count > maxCount) {
                maxCount = count;
            }
        }
        for (String field : output.keySet()) {
            output.get(field).addAll(found.get(field));
            if (found.get(field).size() < maxCount) {
                String filler = "";
                if (found.get(field).size() > 0) {
                    filler = found.get(field).get(0);
                }
                for (int i = found.get(field).size(); i < maxCount; i++) {
                    output.get(field).add(filler);
                }
            }
            if (field.startsWith(extractLevel)) {
                found.get(field).clear();
            }
        }
    }


    public static int readBook(File uploadedFile, Map<String, WizardField> wizardFields, boolean usDates, Map<String, String> lookups) throws Exception {
        OPCPackage opcPackage;
        try (FileInputStream fi = new FileInputStream(uploadedFile)) { // this will hopefully guarantee that the file handler is released under windows
            opcPackage = OPCPackage.open(fi);
        } catch (Exception e) {
            e.printStackTrace();
            throw new IOException("Cannot load file");
        }
        org.apache.poi.ss.usermodel.Workbook book = new XSSFWorkbook(opcPackage);

        org.apache.poi.ss.usermodel.Sheet sheet = book.getSheetAt(0);
        Map<Integer, String> colMap = new HashMap<>();
        int lineCount = -1;
        int colCount = 0;
        try {
            boolean firstRow = true;
            for (org.apache.poi.ss.usermodel.Row row : sheet) {
                int col = 0;
                for (Iterator<org.apache.poi.ss.usermodel.Cell> ri = row.cellIterator(); ri.hasNext(); ) {
                    org.apache.poi.ss.usermodel.Cell cell = ri.next();
                    while (cell.getColumnIndex() > col) {
                        wizardFields.get(colMap.get(col++)).getValuesFound().add("");
                    }
                    String value = ImportService.getCellValueUS(cell, usDates);
                    if (value == null) value = "";
                    if (firstRow) {
                        colMap.put(cell.getColumnIndex(), value);
                        WizardField wizardField = new WizardField(value, ImportUtils.insertSpaces(value), false);
                        wizardField.setValuesFound(new ArrayList<>());
                        wizardFields.put(ImportUtils.headingFrom(value, lookups), wizardField);
                        colCount = wizardFields.size();

                    } else {
                        wizardFields.get(colMap.get(cell.getColumnIndex())).getValuesFound().add(value);
                    }
                    col++;
                }
                while (col < colCount) {
                    wizardFields.get(colMap.get(col++)).getValuesFound().add("");
                }

                firstRow = false;
                lineCount++;
            }
        } catch (Exception e) {
            throw e;
        }
        for (String field : wizardFields.keySet()) {
            ImportUtils.setDistinctCount(wizardFields.get(field));
        }
        return lineCount;
    }


       public static Map<String, WizardField> readCSVFile(String path, String fileEncoding) throws IOException {
        Map<Integer, WizardField> columnMap;
        //adaptes from 'getLinesWithValuesInColumn from dsImportService
        Charset charset = StandardCharsets.UTF_8;
        if ("ISO_8859_1".equals(fileEncoding)) {
            charset = StandardCharsets.ISO_8859_1;
        }
        char delimiter = getDelimiter(path, charset);
        if (delimiter == ' ') {
            charset = StandardCharsets.ISO_8859_1;
            delimiter = getDelimiter(path, charset);
            fileEncoding = "ISO_8859_1";
        }
        if (delimiter == ' ') {
            throw new IOException("cannot read file - tried UTF_8 and ISO_8859_1");
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
        Map<String, WizardField> toReturn = new LinkedHashMap<>();
        int count = 0;
        String firstFieldName = null;
        for (String field : fields) {
            WizardField wizardField = new WizardField(field, ImportUtils.insertSpaces(field), false);
            if (firstFieldName == null) {
                firstFieldName = wizardField.getName();
            }
            //try to deal with fields called 'name' by being more specific.
            if (wizardField.getName().toLowerCase(Locale.ROOT).equals("name") && (firstFieldName.toLowerCase(Locale.ROOT).endsWith(" id") || firstFieldName.toLowerCase(Locale.ROOT).endsWith(" key"))) {
                wizardField.setName(firstFieldName.substring(0, firstFieldName.lastIndexOf(" ")) + " name");


            }
            wizardField.setValuesFound(data.get(count++));
            ImportUtils.setDistinctCount(wizardField);
            toReturn.put(ImportUtils.standardise(field), wizardField);
        }

        return toReturn;
    }

    private static char getDelimiter(String path, Charset charset) {
        try {
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
        } catch (Exception e) {
            return ' ';
        }
    }

    private static List<ImportStage> createStageList(int stage) {
        List<ImportStage> toReturn = new ArrayList<>();
          if (stage==MATCHSTAGE) {
            ImportStage importStage = new ImportStage(stage, MATCHSTAGE, "Identify");
            importStage.setStageComment("match the import columns");
            importStage.setFieldHeadings("Field name,Samples, Imported name");
            importStage.setFields("fieldName,valuesFound,listEntry");
            importStage.setInstructions("Please match the headings on the import file with the values you want to import.");
            importStage.setTitle(importStage.getStageName());
            toReturn.add(importStage);
            return  toReturn;
        }
        ImportStage importStage = new ImportStage(stage, NAMESTAGE, "Properties");
        importStage.setStageComment("rename or exclude columns");
        if (stage == NAMESTAGE) {
            importStage.setFieldHeadings("Imported name,Samples,Suggested name,");
            importStage.setFields("fieldName,valuesFound,textEntry,checkEntry");
            importStage.setInstructions("Please review the <b>suggested</b> name for each field. You can also <b>exclude</b> columns from the import by unselecting the row.");
            importStage.setTitle(importStage.getStageName());
        }
        toReturn.add(importStage);

        importStage = new ImportStage(stage, TYPESTAGE, "Properties");
        importStage.setStageComment("identify key fields");
        if (stage == TYPESTAGE) {
            importStage.setFields("fieldName,valuesFound,listEntry");
            importStage.setFieldHeadings("Field Name, Sample, Field type");
            importStage.setInstructions("identify key field ids, names, and data/time fields");
            importStage.setTitle(importStage.getStageName());
        }
        toReturn.add(importStage);

        importStage = new ImportStage(stage, DATASTAGE, "Data");
        importStage.setStageComment("which fields are values?");
        if (stage == DATASTAGE) {
            importStage.setFields("fieldName,valuesFound,checkEntry,check2Entry");
            importStage.setFieldHeadings("Field Name,Samples,Values,Peers");
            importStage.setTypeName("Data type");
            importStage.setInstructions("<p><strong>Values</strong> are usually additive numbers, but need not be</p> +" +
                    "<p><strong>Peers</strong> are the fields necessary to define the values</br>" +
                    "If there is a key field, it is probably the sole peer." +
                    "</br> ...but budget information may have many peers (e.g. period, division, version)");
            importStage.setTitle(importStage.getStageName());
        }
        toReturn.add(importStage);


        importStage = new ImportStage(stage, PARENTSTAGE, "Relationships");
        importStage.setStageComment("identify parent-child relationships or attributes");
        if (stage == PARENTSTAGE) {
            importStage.setFields("fieldName,listEntry,list2Entry, example");
            importStage.setFieldHeadings("PARENT,CHILD,ANCHOR");
            importStage.setInstructions("   <p>\n" +
                    "Please define <strong>parent-child</strong> relationships.  These allow you to categorise data. " +
                    "Typical relationships include:" +
                    "</p>" +
                    " <ul>" +
                    " <li>Customer &gt; Order &gt; Order Item</li>" +
                    " <li>Country &gt; Town &gt; Street</li>" +
                    " </ul>" +
                    "<p><strong>attributes</strong> are values you want to retain, but which refer only to one other name - the <strong>anchor</strong></p>" +
                    "<ul>" +
                    "<li>telephone number is an attribute of person</li>" +
                    "<li>elevation is an attribute of location</li>" +
                    "</ul>");

            importStage.setTitle(importStage.getStageName());


        }
        toReturn.add(importStage);
        importStage = new ImportStage(stage, SPECIALSTAGE, "Special instructions");
        importStage.setStageComment("");
        if (stage == SPECIALSTAGE) {
            importStage.setFields("fieldName,textEntry");
            importStage.setFieldHeadings("FIELD NAME,SPECIAL INSTRUCTIONS");
            importStage.setInstructions("   <p>\n" +
                    "Here you can enter any further instructions (e.g ignoring certain values, specifying fields as 'local', only importing existing values etc).</p>" +
                    "<p>A full list of special instructions can be found in the manual</p>");

            importStage.setTitle(importStage.getStageName());


        }


        toReturn.add(importStage);

        return toReturn;
     }

    private static Book preparePreprocessor(LoggedInUser loggedInUser)throws Exception{
         WizardInfo wizardInfo = loggedInUser.getWizardInfo();
         ImportTemplate importTemplate = ImportTemplateDAO.findForNameAndBusinessId(loggedInUser.getDatabase().getName() + " " + ImportUtils.getTemplateName(loggedInUser) + " Preprocessor", loggedInUser.getBusiness().getId());
         if (importTemplate==null){
             importTemplate = ImportTemplateDAO.findForName("Default Preprocessor.xlsx");
         }
        Book book = Importers.getImporter().imports(new File(SpreadsheetService.getHomeDir() + ImportService.dbPath + loggedInUser.getBusinessDirectory() + ImportService.importTemplatesDir + importTemplate.getFilenameForDisk()), "Report name");
        io.keikai.api.model.Sheet sheet = book.getSheetAt(0);
        SName inputRegion = BookUtils.getNameByName("az_Input", sheet);
       int inputRowNo = inputRegion.getRefersToCellRegion().getRow();
        SName outputRegion = BookUtils.getNameByName("az_Output", sheet);
        int outputRowNo = outputRegion.getRefersToCellRegion().getRow();
        int sourceCol = 0;
        for (String field:wizardInfo.getMatchFields().keySet()){
            WizardField wizardField = wizardInfo.getMatchFields().get(field);
            ImportUtils.setKeikaiCell(sheet,outputRowNo,sourceCol, wizardField.getName());
            sourceCol++;

        }
        int targetCol = 0;
         for (String field:wizardInfo.getFields().keySet()){
            WizardField wizardField = wizardInfo.getFields().get(field);
            ImportUtils.setKeikaiCell(sheet,outputRowNo,targetCol, wizardField.getName());
            if (wizardField.getMatchedFieldName()!=null){
                sourceCol = ImportUtils.findColNo(wizardInfo.getMatchFields(),wizardField.getMatchedFieldName());
                if (sourceCol==-1){
                    throw new Exception("cannot find " + wizardField.getMatchedFieldName());
                }
                String formula = "=if(" + ImportUtils.cellAsString(inputRowNo + 1, sourceCol)+ ">\"\"," + ImportUtils.cellAsString(inputRowNo + 1, sourceCol) + ",\"\")";
                ImportUtils.setKeikaiCell(sheet,outputRowNo + 1,targetCol, formula);
            }
            targetCol++;

        }
        return book;


    }

    private static List<ImportWizardField> createMatchList(WizardInfo wizardInfo){
        List<ImportWizardField>toReturn = new ArrayList<>();
        List<String>importHeadings = new ArrayList<>();
        for (String matchField:wizardInfo.getMatchFields().keySet()){
            importHeadings.add(matchField);
        }

        for (String field:wizardInfo.getFields().keySet()) {
            WizardField wizardField = wizardInfo.getFields().get(field);
            ImportWizardField importWizardField = new ImportWizardField(wizardField.getName());
            List<String> importChoice = new ArrayList<>(importHeadings);

            String selected = null;
            for (String testHeading:wizardInfo.getFields().keySet()){
                WizardField testField = wizardInfo.getFields().get(testHeading);
                if (!testHeading.equals(field)){
                    String matchedField2 = testField.getMatchedFieldName();
                    if (matchedField2!=null){
                        importChoice.remove(matchedField2);
                    }
                }else {
                    selected = testField.getMatchedFieldName();
                }

            }
            importWizardField.setValuesFound(get100(wizardField.getValuesFound(), wizardField.getValueFound()));
            List<String> listEntry = new ArrayList<>();
            listEntry.add("");
            for (String choice:importChoice){
                if (choice.equals(selected)){
                    listEntry.add(choice + " selected");
                }else
                    listEntry.add(choice);
            }
            importWizardField.setFieldName(wizardField.getName());
            importWizardField.setListEntry(listEntry);
            toReturn.add(importWizardField);
        }
        return toReturn;
    }

    private static List<ImportWizardField> createWizardFieldList(LoggedInUser loggedInUser, int stage, String chosenField, String chosenValue, String dataParent) {
        Map<String, WizardField> wizardFields = loggedInUser.getWizardInfo().getFields();
        List<ImportWizardField> toReturn = new ArrayList<>();
        List<String> peers = new ArrayList<>();
        if (dataParent != null && dataParent.length() > 0) {
            for (String field : wizardFields.keySet()) {
                WizardField wizardField = wizardFields.get(field);
                if (dataParent.equals(wizardField.getParent())) {
                    peers = wizardField.getPeers();
                }
            }
        }
        List<String> possibleParents = new ArrayList<>();
        if (stage == PARENTSTAGE) {

            for (String field : wizardFields.keySet()) {
                WizardField wizardField = wizardFields.get(field);
                if (wizardField.getSelect() && !KEYFIELDID.equals(wizardField.getType()) && !KEYFIELDNAME.equals(wizardField.getType()) && wizardField.getParent() == null && wizardField.getAnchor() == null) {
                    possibleParents.add(field);
                }
            }

        }

        try {
            if (chosenField != null) {
                   ImportUtils.getDataValuesFromFile(loggedInUser.getWizardInfo(), chosenField, chosenValue);
            }


            int count = 1;
            for (String field : wizardFields.keySet()) {

                WizardField wizardField = wizardFields.get(field);
                if (wizardField.getSelect() && wizardField.getValueFound() == null && wizardField.getValuesFound() != null && wizardField.getValuesFound().size() > 0) {
                    ImportUtils.getDataValuesFromFile(loggedInUser.getWizardInfo(), field, wizardField.getValuesFound().iterator().next());
                }
                if (stage == NAMESTAGE || wizardField.getSelect()) {
                    ImportWizardField importWizardField = new ImportWizardField(field);
                    switch (stage) {
                           case NAMESTAGE:
                            importWizardField.setFieldName(field);
                            importWizardField.setValuesFound(get100(wizardField.getValuesFound(), wizardField.getValueFound()));
                            importWizardField.setTextEntry(wizardField.getName());
                            importWizardField.setListEntry(null);
                            String checkstatus = "checked=true";
                            if (!wizardField.getSelect()) {
                                checkstatus = "";
                            }
                            importWizardField.setCheckEntry(checkBoxHTML(1, count, wizardField.getSelect(), false));
                            break;
                        case TYPESTAGE:
                            importWizardField.setFieldName(wizardField.getName());
                            importWizardField.setValuesFound(get100(wizardField.getValuesFound(), wizardField.getValueFound()));
                            importWizardField.setListEntry(new ArrayList<>());
                            importWizardField.getListEntry().add("");
                            for (String type : ImportUtils.TYPEOPTIONS) {
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
                            if (ImportUtils.isKeyField(wizardField)) {
                                importWizardField.setCheckEntry(".");
                            } else {
                                importWizardField.setCheckEntry(checkBoxHTML(1, count, dataParent.equals(wizardField.getParent()), true));
                            }
                            if (KEYFIELDNAME.equals(wizardField.getType()) || wizardField.getParent() != null) {
                                importWizardField.setCheck2Entry(".");
                            } else {
                                importWizardField.setCheck2Entry(checkBoxHTML(2, count, peers.contains(field), false));
                            }
                            break;

                        case PARENTSTAGE:
                            List<String> possibleChildren = new ArrayList<>();
                            List<String> possibleAnchors = new ArrayList<>();
                            possibleChildren.add("");
                            possibleAnchors.add("");
                            importWizardField.setExample("");
                            importWizardField.setFieldName(wizardField.getName());

                            if (wizardField.getParent() != null) {
                                importWizardField.setExample("Value in " + wizardField.getParent());
                                importWizardField.setListEntry(possibleChildren);
                                importWizardField.setList2Entry(possibleAnchors);
                                break;
                            }
                            if (KEYFIELDID.equals(wizardField.getType())) {
                                importWizardField.setExample(KEYFIELDID);
                                importWizardField.setListEntry(possibleChildren);
                                importWizardField.setList2Entry(possibleAnchors);
                                break;
                            }

                            String currentChild = null;
                            String childValue = null;
                            if (possibleParents.contains(field) && wizardField.getAnchor() == null) {
                                //check we have some sample values
                                if (wizardField.getValueFound().length() == 0) {
                                    for (String value : wizardField.getValuesFound()) {
                                        if (value.length() > 0) {
                                            ImportUtils.getDataValuesFromFile(loggedInUser.getWizardInfo(), field, value);
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
                            break;
                        case SPECIALSTAGE:
                            importWizardField.setTextEntry(wizardField.getSpecialInstructions());
                            importWizardField.setExample("");
                            importWizardField.setFieldName(wizardField.getName());

                    }
                    toReturn.add(importWizardField);
                    count++;
                }


            }
        } catch (Exception w) {
            //TODO....
        }
        return toReturn;

    }

    private static String getChildValue(Map<String, WizardField> wizardFields, WizardField childField) {
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

    private static String checkBoxHTML(int checkColumn, int fieldNo, boolean checked, boolean hasOnChange) {
        String checkstatus = "checked=true";
        if (!checked) {
            checkstatus = "";
        }

        if (hasOnChange) {
            checkstatus += " onChange=\"checkChanged(" + fieldNo + ")\"";
        }
        return "<input type=\"checkbox\" id=\"check" + checkColumn + "-" + fieldNo + "\" " + checkstatus + "\">";

    }

    private static List<String> get100(List<String> source, String selected) {
        List<String> target = new ArrayList<>();
        if (source==null){
            return target;
        }
        boolean sel = false;
        int count = 0;
        for (String element : source) {
            if (element.equals(selected) && !sel) {
                target.add(element + " selected");
                sel = true;
            } else {
                target.add(element);
            }
            if (++count == 100) {
                break;
            }
        }
        return target;

    }

    public static String handleRequest(LoggedInUser loggedInUser, HttpServletRequest request) {



        WizardInfo wizardInfo = loggedInUser.getWizardInfo();

        final ObjectMapper jacksonMapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        jacksonMapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
        String chosenField = request.getParameter("chosenfield");
        String chosenValue = request.getParameter("chosenvalue");
        int stage = Integer.parseInt(request.getParameter("stage"));
        int nextStage = Integer.parseInt(request.getParameter("nextstage"));
        String dataParent = request.getParameter("dataparent");
        String fieldsInfo = request.getParameter("fields");
        if (wizardInfo.getMatchFields()!=null){

            if (nextStage!=EXCELSTAGE){
                nextStage = MATCHSTAGE;
            }
            stage = MATCHSTAGE;
        }
        if (stage!=EXCELSTAGE){
            try {
                Book book = Books.createBook("new book");
                Ranges.range(book).createSheet("Sheet1");
                request.setAttribute(OnlineController.BOOK, book); // set up a blank book in case this hits the ImportWizard...
            }catch(Exception e){
            }
        }
        if (stage!=NAMESTAGE && chosenField!=null && chosenField.length()>0){
            chosenField = ImportUtils.findFieldFromName(wizardInfo,chosenField);
        }

        if(chosenValue==null || chosenValue.length()==0){
            String error = processFound(wizardInfo, fieldsInfo, stage, dataParent);
            if (error != null) {
                return "error:" + error;
            }
        }
        stage = nextStage;
        try {
              Map<String, List<ImportStage>> output = new HashMap<>();
            List<ImportStage> stages = createStageList(nextStage);
            if (nextStage!=MATCHSTAGE && nextStage!=EXCELSTAGE) {
                if (nextStage > wizardInfo.getMaxStageReached()) {
                    String suggestion = ImportSuggestion.makeSuggestion(wizardInfo, nextStage);
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
            }

            output.put("stage", stages);
            String toReturn = jacksonMapper.writeValueAsString(output);
            List<ImportWizardField> wizardFields = null;
            if (nextStage == MATCHSTAGE){
                if (chosenField != null) {
                    ImportUtils.getDataValuesFromFile(loggedInUser.getWizardInfo(), chosenField, chosenValue);
                }
                wizardFields = createMatchList(wizardInfo);
            }else if (nextStage == EXCELSTAGE) {
                Book book = preparePreprocessor(loggedInUser);//NOTE Keikai book
                request.setAttribute(OnlineController.BOOK, book); // push the rendered book into the request to be sent to the user
                return "wizardimport";

            }else{
                wizardFields = createWizardFieldList(loggedInUser, nextStage, chosenField, chosenValue, dataParent);
            }
            Map<String, List<ImportWizardField>> fields = new HashMap<>();
            fields.put("field", wizardFields);
            String fieldInfo = jacksonMapper.writeValueAsString(fields);
            toReturn += fieldInfo;
            return toReturn.replace("}{", ",");//not proud of this!


        } catch (Exception e) {
            return "error:" + e.getMessage();
        }
    }

    public static String uploadTheFile(LoggedInUser loggedInUser, UploadedFile uploadFile) {
        String importName = uploadFile.getFileName();
        importName = importName.substring(0, importName.indexOf(" "));
         try {
            ImportTemplate importTemplate = null;
            int importTemplateId = loggedInUser.getDatabase().getImportTemplateId();
            if (importTemplateId>0) {
                importTemplate = ImportTemplateDAO.findById(importTemplateId);
            }

            if (importTemplate==null) {
                String templateName = loggedInUser.getDatabase().getName() + " Import Templates.xlsx";
                importTemplate = ImportTemplateDAO.findForNameAndBusinessId(templateName,loggedInUser.getBusiness().getId());
                //create a new blank one.
                if (importTemplate==null) {
                    Workbook wb = new XSSFWorkbook();
                    String tempPath = SpreadsheetService.getHomeDir() + "/temp/" + System.currentTimeMillis() + " Import Templates.xlsx"; // timestamp the upload to stop overwriting with a file with the same name is uploaded after

                    FileOutputStream os = new FileOutputStream(tempPath);
                    wb.write(os);
                    os.close();
                    List<String> fileNames = new ArrayList<>();
                    fileNames.add(templateName);
                    UploadedFile uploadedFile = new UploadedFile(tempPath, fileNames, false);
                    uploadedFile = ImportService.uploadImportTemplate(uploadedFile, loggedInUser, "created by Import Wizard");
                }
                importTemplate = ImportTemplateDAO.findForNameAndBusinessId(templateName,loggedInUser.getBusiness().getId());
                if (importTemplate != null) {
                    loggedInUser.getDatabase().setImportTemplateId(importTemplate.getId());
                    DatabaseDAO.store(loggedInUser.getDatabase());
                }else{
                    throw new Exception("cannot create an import template:" + templateName);
                }
            }
            ImportTemplateData importTemplateData = ImportService.getImportTemplateData(importTemplate, loggedInUser);

            interpretTemplateData(loggedInUser, uploadFile.getFileName(), importTemplateData.getSheets().get(importName));
             WizardInfo targetInfo = loggedInUser.getWizardInfo();

            WizardInfo sourceInfo = null;
            String fileName = uploadFile.getFileName();
            if (fileName.toLowerCase(Locale.ROOT).contains(".xls")) {
                Map<String, WizardField> wizardFields = new LinkedHashMap<>();
                sourceInfo = new WizardInfo(fileName, null);
                int lineCount = ImportWizard.readBook(new File(uploadFile.getPath()), wizardFields, false, targetInfo.getLookups());
                sourceInfo.setFields(wizardFields);
                sourceInfo.setLineCount(lineCount);

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

                sourceInfo = new WizardInfo(uploadFile.getFileName(), jsonObject);
            } else {
                Map<String, String> fileNameParameters = new HashMap<>();
                ImportService.addFileNameParametersToMap(uploadFile.getFileName(), fileNameParameters);
                sourceInfo = new WizardInfo(fileName, null);

                Map<String, WizardField> wizardFields = ImportWizard.readCSVFile(uploadFile.getPath(), fileNameParameters.get("fileencoding"));
                //wizardInfo.setFields(wizardFields);
                for (String field : wizardFields.keySet()) {
                    WizardField wizardField = wizardFields.get(field);
                    sourceInfo.setLineCount(wizardField.getValuesFound().size());
                    break;
                }
                sourceInfo.setFields(wizardFields);
            }
            if (targetInfo.getFields().size() > 0) {
                targetInfo.setMaxStageReached(SPECIALSTAGE);
                for (String field : sourceInfo.getFields().keySet()) {
                    WizardField source = sourceInfo.getFields().get(field);
                    WizardField target = targetInfo.getFields().get(field);
                    if (target==null){
                           //needs to be matched.
                        targetInfo.setMatchFields(sourceInfo.getFields());
                        for (String matchField:sourceInfo.getFields().keySet()){
                            WizardField sourceField = sourceInfo.getFields().get(matchField);
                            String matchedField = targetInfo.getLookups().get(matchField);
                            if (matchedField!=null){
                                if (sourceField.getValuesFound().size() > targetInfo.getLineCount()){
                                    targetInfo.setLineCount(sourceField.getValuesFound().size());
                                }
                                matchTheField(targetInfo, targetInfo.getFields().get(matchedField), matchField);
                                targetInfo.getLookups().remove(matchField);//it may be edited...
                            }
                        }

                        return "importwizard";
                    } else {
                         target.setValuesFound(source.getValuesFound());
                         ImportUtils.setDistinctCount(target);
                     }
                }
                targetInfo.setLineCount(sourceInfo.getLineCount());
                setupValueFound(targetInfo);

            } else {
                loggedInUser.setWizardInfo(sourceInfo);
            }

        } catch (Exception e) {
            //todo
            int k = 1;
        }
        return "importwizard";

    }

    private static void matchTheField(WizardInfo wizardInfo, WizardField targetField, String sourceName) {
        if (sourceName!=null) {
            WizardField sourceField = wizardInfo.getMatchFields().get(sourceName);
            targetField.setMatchedFieldName(ImportUtils.standardise(sourceField.getImportedName()));
            targetField.setValuesFound(sourceField.getValuesFound());
            targetField.setDistinctCount(sourceField.getDistinctCount());
            targetField.setValueFound(sourceField.getValueFound());
        }else{
            targetField.setMatchedFieldName(null);
            targetField.setValuesFound(null);
            targetField.setDistinctCount(0);
            targetField.setValueFound(null);
        }


    }


      private static void setupValueFound(WizardInfo wizardInfo){
        // set up .value fields
        for (String field:wizardInfo.getFields().keySet()){
            WizardField wizardField = wizardInfo.getFields().get(field);
            if (wizardField.getValueFound() == null || wizardField.getValueFound().length() == 0) {
                for (String value : wizardField.getValuesFound()) {
                    if (value.length() > 0) {
                        try {
                            ImportUtils.getDataValuesFromFile(wizardInfo, field, value);
                            return;
                        }catch(Exception e){
                            //TODO
                        }
                    }
                }
            }
        }


    }

    private static void uploadData(LoggedInUser loggedInUser, Map<String, List<String>> data) throws Exception {


        Map<String, String> wizardDefs = new LinkedHashMap<>();
        WizardInfo wizardInfo = loggedInUser.getWizardInfo();
        for (String field : wizardInfo.getFields().keySet()) {
            WizardField wizardField = wizardInfo.getFields().get(field);
            String def = wizardField.getImportedName() + ";" + wizardField.getName();

             if (wizardField.getSelect()) {
                def += ";" + wizardField.getInterpretation();
                if (data.get(field) != null && wizardField.getSpecialInstructions().length() > 0) {
                    def += ";" + wizardField.getSpecialInstructions();
                }
                 if (wizardField.getParent()!=null){
                     def += ";child of " + wizardField.getParent();
                 }
             }
              wizardDefs.put(field, def);


        }
        Map<String, String> modifiedHeadings = new LinkedHashMap<>();
        String keyFieldId = null;
        String keyField = null;
        int nameRow = 1;
        int firstClause = 2;
        for (String field : wizardDefs.keySet()) {
            String wizardDef = wizardDefs.get(field);
            String[] clauses = wizardDef.split(";");
            if (clauses.length > firstClause) {
                if (clauses[firstClause].equals("key field id")) {
                    keyFieldId = clauses[nameRow];
                }
            }
        }

        for (String field : wizardDefs.keySet()) {
            //this conversion is complicated by the fact that we do not want the 'id' fields to be names, but do want them to continue as attributes( 'product id' is attribute of 'product')
            String wizardDef = wizardDefs.get(field);
            String[] clauses = wizardDef.split(";");
            StringBuffer modifiedHeading = new StringBuffer();
            if (clauses.length > firstClause) {
                String fieldName = clauses[nameRow];
                String fieldNameSansId = ImportUtils.removeId(fieldName);
                String dataAnchor = getClause("attribute of", clauses);

                if (dataAnchor != null) {
                    dataAnchor = ImportUtils.removeId(dataAnchor);
                    modifiedHeading = new StringBuffer();
                    if (ImportUtils.isIdField(wizardInfo, dataAnchor) && fieldName.toLowerCase(Locale.ROOT).endsWith("name")) {
                        fieldName = "name";
                    }
                    modifiedHeading.append(dataAnchor + ";attribute " + fieldName);
                } else {
                    modifiedHeading.append(fieldNameSansId);
                }
                if (clauses[firstClause].equals(StringLiterals.DATELANG) || clauses[firstClause].equals(StringLiterals.USDATELANG)) {
                    modifiedHeading.append(";datatype " + clauses[firstClause]);

                }
                if (clauses[firstClause].equals("key field name")) {
                    if (keyFieldId != null) {
                        modifiedHeading = new StringBuffer();
                        modifiedHeading.append(ImportUtils.removeId(keyFieldId) + ";attribute name");
                    } else {
                        modifiedHeading.append(";child of " + fieldNameSansId);
                    }
                }
                if (clauses[firstClause].equals("key field id") || ImportUtils.isIdField(wizardInfo, field)) {
                    modifiedHeading.append(";language " + field + ";child of " + fieldNameSansId);
                }
                String dataChild = getClause("parent of", clauses);
                if (dataChild != null) {
                    modifiedHeading.append(";parent of " + ImportUtils.removeId(dataChild) + ";default No " + fieldNameSansId);
                    String childClause = ";child of " + fieldNameSansId;
                    if (!modifiedHeading.toString().contains(childClause)) {
                        modifiedHeading.append(childClause);
                    }

                }
                String dataParent = getClause("datagroup", clauses);
                if (dataParent != null) {
                    CommonReportUtils.getDropdownListForQuery(loggedInUser, "edit:create Data" + StringLiterals.MEMBEROF + dataParent + StringLiterals.MEMBEROF + fieldName);

                }

                String peersString = getClause("peers", clauses);
                if (peersString != null) {
                    if (peersString.toLowerCase(Locale.ROOT).endsWith("id}")) {
                        peersString = peersString.substring(0, peersString.length() - 3).trim() + "}";
                    }
                    modifiedHeading.append(";peers " + peersString);
                }
                String peerFor = getClause("peer for", clauses);
                if (peerFor != null) {
                    String clause = ";child of " + fieldNameSansId;
                    if (!modifiedHeading.toString().contains(clause)) {
                        modifiedHeading.append(";child of " + fieldNameSansId);
                    }
                }
                if (modifiedHeading.length() > 0) {
                    modifiedHeadings.put(field, clauses[0] + ";" + modifiedHeading.toString());
                }
            }else{
                modifiedHeadings.put(field,wizardDef);
            }
        }
        String fileName = wizardInfo.getImportFileName();
        if (fileName.contains(".")) {
            fileName = fileName.substring(0, fileName.indexOf("."));
            ;
        }
        saveTemplate(loggedInUser, modifiedHeadings);
        //now take off the fields to ignore.
        for (String field : wizardInfo.getFields().keySet()) {
            if (!wizardInfo.getFields().get(field).getSelect()) {
                modifiedHeadings.remove(field);
            }
        }


        RMIClient.getServerInterface(loggedInUser.getDatabaseServer().getIp()).uploadWizardData(loggedInUser.getDataAccessToken(), fileName, modifiedHeadings, data);

    }

    private static void interpretTemplateData(LoggedInUser loggedInUser, String importFilename, List<List<String>> templateData) {
        WizardInfo wizardInfo = new WizardInfo(importFilename, null);
        loggedInUser.setWizardInfo(wizardInfo);
        if (templateData == null) {
            templateData = new ArrayList<>();
        }
        int rows = 0;
        while (rows < templateData.size() && templateData.get(rows).size() > 0) {
            rows++;
        }
        if (rows < 2) return;
        Map<String, WizardField> nameMap = new HashMap<>();
        Set<String> attributes = new HashSet<>();
        for (int col = 0; col < templateData.get(0).size(); col++) {
            //the top line is a comma separated list of options for that field - the first name is the original name
            String[] fieldNames = templateData.get(0).get(col).split("\\|\\|");
            String baseName = ImportUtils.standardise(fieldNames[0]);
            for (String fieldName:fieldNames){
                wizardInfo.getLookups().put(ImportUtils.standardise(fieldName), baseName);
            }
            WizardField wizardField = new WizardField(fieldNames[0], templateData.get(1).get(col), false);
            wizardInfo.getFields().put(ImportUtils.standardise(wizardField.getImportedName()), wizardField);
            nameMap.put(wizardField.getName(), wizardField);
            wizardField.setSelect(false);
            for (int row = 2; row < rows; row++) {
                if (col < templateData.get(row).size()){
                    String clause = templateData.get(row).get(col);
                    if (clause.length()>0){
                        wizardField.getTemplateClauses().add(clause);
                        if (clause.startsWith(StringLiterals.ATTRIBUTE))  {
                            attributes.add(ImportUtils.standardise(wizardField.getImportedName()));
                        }
                    }
                }
            }
        }
        for (String field: wizardInfo.getFields().keySet()){
            if (!attributes.contains(field)) {
                interpretClauses(loggedInUser, field);
            }
        }
       for (String field: attributes){
             interpretClauses(loggedInUser, field);
        }
       try {
           ImportUtils.calcXL(wizardInfo);
       }catch(Exception e){
           //to do
           int k=1;
       }
    }

    private static void interpretClauses(LoggedInUser loggedInUser, String field) {
        WizardInfo wizardInfo = loggedInUser.getWizardInfo();
        WizardField wizardField = wizardInfo.getFields().get(field);
        for (String clause:wizardField.getTemplateClauses()){
            int firstBlank = clause.indexOf(" ");
            String clauseData = "";
            if (firstBlank > 0) {
                clauseData = clause.substring(firstBlank).trim();
            }
            if (clause != null && clause.length() > 0) {
                wizardField.setSelect(true);
                if (clause.startsWith(StringLiterals.PEERS)) {
                    if (clauseData.startsWith("{") && clauseData.endsWith("}")) {
                        List<String> peers = Arrays.asList(clauseData.substring(1, clauseData.length() - 1).split(","));
                        wizardField.setPeers(new ArrayList<>());
                        for (String peer : peers) {
                            wizardField.getPeers().add(ImportUtils.findFieldFromName(wizardInfo, peer));
                            try {
                                String parent = RMIClient.getServerInterface(loggedInUser.getDataAccessToken().getServerIp()).getNameAttribute(loggedInUser.getDataAccessToken(), wizardField.getName(), "Data");
                                if (parent != null) {
                                    wizardField.setParent(parent);
                                }
                            } catch (Exception e) {
                                wizardField.setParent("Unknown");
                            }
                        }
                        if (peers.size() == 1) {
                            String keyPeer = peers.iterator().next();
                            WizardField keyField = wizardInfo.getFields().get(ImportUtils.findFieldFromName(wizardInfo, keyPeer));
                            if (keyField != null) {
                                keyField.setType(KEYFIELDID);
                            }

                        }
                    }
                } else if (clause.startsWith(StringLiterals.PARENTOF)) {
                    clauseData = clause.substring(StringLiterals.PARENTOF.length()).trim();
                    wizardField.setChild(ImportUtils.findFieldFromName(wizardInfo, clauseData));
                } else if (clause.startsWith(".") || clause.startsWith(StringLiterals.ATTRIBUTE)) {
                    if (clause.startsWith(".")) {
                        clauseData = clause.substring(1).trim();
                    }
                    String anchor = ImportUtils.findFieldFromName(wizardInfo, wizardField.getName());
                    wizardField.setAnchor(anchor);
                    if (clauseData.equalsIgnoreCase("name")) {
                        clauseData = wizardInfo.getFields().get(wizardField.getAnchor()).getName() + " Name";
                        if (KEYFIELDID.equals(wizardInfo.getFields().get(anchor).getType())) {
                            wizardField.setType(KEYFIELDNAME);
                        }

                    }
                    wizardField.setName(clauseData);
                } else if (clause.startsWith(StringLiterals.AZEQUALS)) {
                    wizardField.setImportedName(wizardField.getImportedName() + clause.substring(2));
                    wizardField.setAdded(true);
                } else if (clause.startsWith(StringLiterals.DATATYPE)) {
                    wizardField.setType(clauseData);
                } else if (clause.startsWith(StringLiterals.LANGUAGE) && clauseData.startsWith(field)) {
                    //ignore

                } else if (clause.startsWith(StringLiterals.CHILDOF)) {
                    clauseData = clause.substring(StringLiterals.CHILDOF.length()).trim();
                    if (!wizardField.getName().startsWith(clauseData)) {
                        if (wizardField.getPeers() != null) {
                            wizardField.setParent(clauseData);
                        } else {
                            addSpecialInstructions(wizardField, clause);
                        }
                    }
                } else if (clause.startsWith(StringLiterals.DEFAULT)) {
                    if (!("no " + wizardField.getName()).equalsIgnoreCase(clauseData)) {
                        addSpecialInstructions(wizardField, clause);
                    }


                } else {
                    addSpecialInstructions(wizardField, clause);
                }
            }
        }

    }

    private static void addSpecialInstructions(WizardField wizardField, String clause) {
        String specialInstructions = wizardField.getSpecialInstructions();
        if (specialInstructions.length() > 0) {
            specialInstructions += ";";


        }
        wizardField.setSpecialInstructions(specialInstructions + clause);

    }

    private static void saveTemplate(LoggedInUser loggedInUser, Map<String, String> modifieldHeadings) throws Exception {
        ImportTemplate importTemplate = null;
        int importTemplateId = loggedInUser.getDatabase().getImportTemplateId();
        importTemplate = ImportTemplateDAO.findById(importTemplateId);

        String filePath = SpreadsheetService.getHomeDir() + ImportService.dbPath + loggedInUser.getBusinessDirectory() + ImportService.importTemplatesDir + importTemplate.getFilenameForDisk();
        InputStream in = new FileInputStream(filePath);
        Workbook book = new XSSFWorkbook(in);
        in.close();
        String templateName = ImportUtils.getTemplateName(loggedInUser);
        for (int sheetNo = 0; sheetNo < book.getNumberOfSheets(); sheetNo++) {
            if (book.getSheetAt(sheetNo).getSheetName().equals(templateName)) {
                book.removeSheetAt(sheetNo);
                break;
            }
        }
        Sheet sheet = book.createSheet(templateName); // literals not best practice, could it be factored between this and the xlsx file?
        int row = 8;
        int col = 2;
        WizardInfo wizardInfo = loggedInUser.getWizardInfo();
        for (String field : wizardInfo.getFields().keySet()) {
            WizardField wizardField = wizardInfo.getFields().get(field);

            ImportService.setCellValue(sheet, row, col, ImportUtils.lookupList(field,wizardInfo.getLookups()));//a comma separated list
            ImportService.setCellValue(sheet, row, col + 1, wizardField.getName());
            ImportService.setCellValue(sheet, row++, col + 2, wizardField.getInterpretation());
        }
        col = 0;
        for (String field : modifieldHeadings.keySet()) {
            String[] clauses = modifieldHeadings.get(field).split(";");
            int rowNo = 0;
            for (String clause : clauses) {
                if (rowNo==0){
                    //create a comme separated list of potential column names
                    clause = ImportUtils.lookupList(ImportUtils.standardise(clause), wizardInfo.getLookups());
                }
                ImportService.setCellValue(sheet, rowNo++, col, clause);
            }
            col++;

            //ImportService.setCellValue(sheet,0, col++, modifieldHeadings.get(field));
        }

        FileOutputStream out = new FileOutputStream(filePath);
        book.write(out);
        out.close();

        /*
        templateName = loggedInUser.getDatabase().getName() + " " + templateName + " Import template.xlsx";
        String path = SpreadsheetService.getHomeDir() + "/temp/" + System.currentTimeMillis() + templateName;
         File moved = new File(path); // timestamp to stop file overwriting

        try {
            File writeFile = new File(path);
            writeFile.delete(); // to avoid confusion
            //not move it to permanent pos

            OutputStream outputStream = new FileOutputStream(writeFile) ;
            book.write(outputStream);
            outputStream.close();
            List<String> fileNames = new ArrayList<>();
            fileNames.add(templateName);
            UploadedFile uploadedFile = new UploadedFile(path, fileNames, false);
            ImportService.uploadImportTemplate(uploadedFile,loggedInUser,"created by the wizard");
            writeFile.delete();
        } catch (Exception e) {

            // Display exceptions along with line number
            // using printStackTrace() method
            e.printStackTrace();
        }

         */
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
