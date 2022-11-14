package com.azquo.dataimport;

import com.azquo.DateUtils;
import com.azquo.ExternalConnector;
import com.azquo.StringLiterals;
import com.azquo.admin.database.DatabaseDAO;
import com.azquo.admin.onlinereport.ExternalDatabaseConnection;
import com.azquo.admin.onlinereport.ExternalDatabaseConnectionDAO;
import com.azquo.admin.onlinereport.OnlineReport;
import com.azquo.admin.onlinereport.OnlineReportDAO;
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
import io.keikai.api.*;
import io.keikai.api.model.Book;
import io.keikai.model.CellRegion;
import io.keikai.model.SCell;
import io.keikai.model.SName;
import io.keikai.model.SSheet;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.lang.math.NumberUtils;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.AreaReference;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.XML;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.ui.ModelMap;
import org.zkoss.util.media.AMedia;
import org.zkoss.zk.ui.Sessions;
import org.zkoss.zk.ui.util.Clients;
import org.zkoss.zul.Filedownload;

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


    static final String AZOUTPUT = "az_Output";

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
            int lastFieldPos = jsonField.lastIndexOf(Preprocessor.JSONFIELDDIVIDER);
            this.source = Preprocessor.JSONFIELDDIVIDER + jsonField.substring(0, lastFieldPos);
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
    public static final String IDSUFFIX = " (KEY FIELD ID)";
    public static final String KEYFIELDNAME = "key field name";
    public static final int MATCHSTAGE = 0;
    public static final int NAMESTAGE = 1;
    public static final int TYPESTAGE = 2;
    public static final int DATASTAGE = 3;
    public static final int PARENTSTAGE = 4;
    public static final int SPECIALSTAGE = 5;
    public static final int SHOWSPREADSHEETDATASTAGE = 7;
    public static final String SPREADSHEETCALCULATION = "SPREADSHEET CALCULATION";
    public static final String IMPORTFILENAMEREGEX = "import file name regex";

    static ImportTemplateData importTemplateData;

    static ServletContext servletContext = null;

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

    public static String processFound(WizardInfo wizardInfo, String fields, int stage) {
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
                Map<String,String> mapping = new HashMap<>();
                for (String line : fieldLines) {
                    String[] elements = line.split("\t");

                    if (elements.length > 2 && elements[2].length() > 0) {
                        mapping.put(ImportUtils.findFieldFromName(wizardInfo, elements[2].replace(IDSUFFIX,"")), elements[0]);
                    }
                }
                for (String field:wizardInfo.getFields().keySet()) {
                    WizardField wizardField = wizardInfo.getFields().get(field);
                    matchTheField(wizardInfo, wizardField, null);
                    ;
                    if (mapping.get(field) != null) {
                        matchTheField(wizardInfo, wizardField, mapping.get(field));
                    }
                }

                break;
            case NAMESTAGE:
                List<String> toDelete = new ArrayList<>();
                for (String line : fieldLines) {
                    String[] elements = line.split("\t");
                    if (elements[0].length() > 0) {
                        String shownName = elements[0];
                        if (elements.length > 2){
                            shownName = elements[2];

                        }
                        WizardField wizardField = wizardInfo.getFields().get(elements[0]);
                        if (wizardField == null) {
                            wizardField = new WizardField(elements[0], shownName, true);
                            wizardInfo.getFields().put(elements[0], wizardField);
                        }
                        //imported name, values, name, selected
                        if (!wizardField.getName().equalsIgnoreCase(shownName)) {
                            if (usedName(wizardInfo, shownName)) {
                                return "error: duplicate name " + shownName;
                            }
                        }
                        wizardField.setName(shownName);
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
                        if(elements[2].equals(KEYFIELDID)|| elements[2].equals(KEYFIELDNAME)){
                            clearField(wizardField);
                        }
                        wizardField.setType(elements[2]);
                    }
                }
                break;

            case DATASTAGE:
                String dataParent = wizardInfo.getImportSchedule().getTemplateName() + " Values";
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
                    if (fieldInfo.length > 2) {
                        wizardField.setSpecialInstructions(fieldInfo[2]);
                    } else {
                        wizardField.setSpecialInstructions("");
                    }
                }
                try{
                    ImportUtils.calcXL(wizardInfo, 100);
                }catch (Exception e){
                    //TODO
                    return e.getMessage();
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
            if (ImportUtils.areNumbers(wizardField.getValuesFound())){
                toReturn.append(";language " +wizardField.getName() + "id");
            }
        }
        if (wizardField.getAnchor() != null && !KEYFIELDID.equals(wizardField.getType())) {
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
                if (wizardPeer==null){//peers problem
                    return;
                }
                if (!firstPeer) toReturn.append(",");
                toReturn.append(wizardPeer.getName());
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

    public static void importFromFile(LoggedInUser loggedInUser) throws Exception {
        WizardInfo wizardInfo = loggedInUser.getWizardInfo();
        if (wizardInfo.getMatchFields()==null){
            ImportUtils.calcXL(wizardInfo, 0);
        }
        wizardInfo.getExtraTemplateFields().put(wizardInfo.getCurrentTemplate(),wizardInfo.getFields());
        Set<String>templatesUsed = new HashSet<>();
        if (wizardInfo.getExtraTemplateFields().size()>1){
            savePreprocessor(loggedInUser);
        }else {
            boolean hasPreprocessor = false;
            for (String template : wizardInfo.getExtraTemplateFields().keySet()) {
                for (String field : wizardInfo.getFields().keySet()) {
                    WizardField wizardField = wizardInfo.getFields().get(field);
                    if (wizardField.getMatchedFieldName() != null) {
                        templatesUsed.add(template);
                    }
                    //fill in the rest of the calculated fields.
                    if (templatesUsed.size() > 1 || SPREADSHEETCALCULATION.equals(wizardField.getMatchedFieldName())) {
                        savePreprocessor(loggedInUser);
                        wizardInfo.templateParameters.put("PREPROCESSOR", getDBName(loggedInUser) + " " + wizardInfo.getImportSchedule().getTemplateName() + " Preprocessor.xlsx");
                        calcSpreadsheetData(loggedInUser, wizardInfo.getLineCount());
                        hasPreprocessor = true;
                        break;
                    }
                }
                if (hasPreprocessor) {
                    break;
                }
            }
        }
        String dataField = null;
        Set<String> pathsRequired = new HashSet<>();
        Map<String, List<String>> flatfileData = new LinkedHashMap<>();
        Map<String, List<String>> onePassValues = new HashMap<>();
        boolean isJSON = true;
        for (String field : wizardInfo.getFields().keySet()) {
            WizardField wizardField = wizardInfo.getFields().get(field);
            if (wizardField.getMatchedFieldName()!=null && !wizardField.getMatchedFieldName().equals(field) && !SPREADSHEETCALCULATION.equals(wizardField.getMatchedFieldName())){
                wizardInfo.getLookups().put(wizardField.getMatchedFieldName(), field);
            }
            setInterpretation(wizardInfo, wizardField);
            if (wizardField.getSelect() && wizardField.getInterpretation().length() > 0) {
                if (!field.contains(" where ")) {
                    pathsRequired.add(Preprocessor.JSONFIELDDIVIDER + field);
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
            if (wizardField.getSelect()  && (ImportUtils.DATELANG.equals(wizardField.getType()) || ImportUtils.USDATELANG.equals(wizardField.getType()))) {
                flatfileData.put(field, ImportUtils.adjustDates(flatfileData.get(field), wizardField.getType()));
            }
            if (wizardField.getSelect() && "time".equals(wizardField.getType())) {
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
            String newPath = jsonPath + Preprocessor.JSONFIELDDIVIDER + jsonName;
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
            String newPath = jsonPath + Preprocessor.JSONFIELDDIVIDER + jsonName;
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

                        if (!SPREADSHEETCALCULATION.equals(wizardField.getMatchedFieldName())){
                            wizardField.setValuesFound(new ArrayList<>());
                        }
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




    public static WizardInfo readCSVFile(String path, String fileEncoding, WizardInfo wizardInfo)throws Exception{
        try{
            return readCSVFile1(path, fileEncoding, wizardInfo);
        }catch(Exception e){
            try {
                if (fileEncoding == null) {
                    return readCSVFile1(path, "ISO_8859_1", wizardInfo);
                }
            }catch (Exception e2){
                throw new Exception("cannot read file - tried UTF_8 and ISO_8859_1");
            }
        }
        throw new Exception("cannot read file - tried " + fileEncoding);

    }

    public static WizardInfo readSQL(LoggedInUser loggedInUser, WizardInfo wizardInfo)throws Exception{

        String sql = wizardInfo.getImportSchedule().getSql();
        if (!sql.toLowerCase(Locale.ROOT).contains(" limit ")){
            if (sql.endsWith(";")){
                sql = sql.substring(0,sql.length()-1) + " limit 10000";
            }else{
                sql += " limit 10000";
            }
        }
        ExternalDatabaseConnection externalDatabaseConnection = ExternalDatabaseConnectionDAO.findById(wizardInfo.getImportSchedule().getConnectorId());
        if (externalDatabaseConnection!=null){
            List<List<String>> data = ExternalConnector.getData(loggedInUser, externalDatabaseConnection.getId(), CommonReportUtils.replaceUserChoicesInQuery(loggedInUser, sql), null, null);
            List<List<String>> fieldData = new ArrayList<>();
            List<String> fields = new ArrayList<>();
            for (int lineNo = 0; lineNo < data.size(); lineNo++){
                List<String> line = data.get(lineNo);
                for(int fieldNo = 0; fieldNo < line.size();fieldNo++){
                    if (lineNo == 0){
                        fields.add(line.get(fieldNo));
                        fieldData.add(new ArrayList<>());
                    }else{
                        fieldData.get(fieldNo).add(line.get(fieldNo));
                    }
                 }
            }

            WizardInfo sourceInfo = new WizardInfo(null , null, null);
            sourceInfo.setLineCount(data.size()-1);
            for (int fieldNo=0;fieldNo < fields.size();fieldNo++) {
                WizardField wizardField = new WizardField(fields.get(fieldNo), ImportUtils.humaniseName(fields.get(fieldNo)), false);
                wizardField.setValuesFound(fieldData.get(fieldNo));
                ImportUtils.setDistinctCount(wizardField);
                sourceInfo.getFields().put(fields.get(fieldNo),wizardField);
            }
            return  sourceInfo;
        }
        return null;

    }



    public static WizardInfo readCSVFile1(String path, String fileEncoding, WizardInfo wizardInfo) throws IOException {
         //adaptes from 'getLinesWithValuesInColumn from dsImportService
        Charset charset = StandardCharsets.UTF_8;
        if ("ISO_8859_1".equals(fileEncoding)) {
            charset = StandardCharsets.ISO_8859_1;
        }
        char delimiter = getDelimiter(path, charset);
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
        int lineCount = 0;
        List<Integer> badLines = new ArrayList<>();
        while (lineIterator.hasNext() && lineCount++ < 10000) {
            if (firstLine) {
                fields = lineIterator.next();
                for (int count = 0; count < fields.length; count++)
                    data.put(count, new ArrayList<>());
                firstLine = false;
            } else {
                try {
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
                }catch(Exception e){
                    badLines.add(lineCount);
                }
            }
        }
        Map<String, WizardField> wizardFields = new LinkedHashMap<>();
        int count = 0;
        String firstFieldName = null;
        for (String field : fields) {
            WizardField wizardField = new WizardField(field, ImportUtils.insertSpaces(field), false);
            if (firstFieldName == null) {
                firstFieldName = wizardField.getName();
            }
            //try to deal with fields called 'name' by being more specific.
            if (wizardField.getName().toLowerCase(Locale.ROOT).equals("name") && ImportUtils.nameField(firstFieldName)!=null) {
                wizardField.setName(ImportUtils.nameField(firstFieldName));


            }
            wizardField.setValuesFound(data.get(count++));
            ImportUtils.setDistinctCount(wizardField);
            wizardFields.put(ImportUtils.standardise(field), wizardField);
        }


        wizardInfo.setFields(wizardFields);
        wizardInfo.setBadLines(badLines);

        return wizardInfo;
    }

    private static char getDelimiter(String path, Charset charset)throws IOException {
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
    }

    private static List<ImportStage> createStageList(int stage) {
        List<ImportStage> toReturn = new ArrayList<>();
        if (stage==MATCHSTAGE) {
            ImportStage importStage = new ImportStage(stage, MATCHSTAGE, "Identify");
            importStage.setStageComment("match the import columns");
            importStage.setFieldHeadings("Imported name,Samples,Matched name");
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
            importStage.setFields("fieldName,valuesFound,textEntry");
            importStage.setFieldHeadings("FIELD NAME,Samples, SPECIAL INSTRUCTIONS");
            importStage.setInstructions("   <p>\n" +
                    "Here you can enter any further instructions (e.g ignoring certain values, specifying fields as 'local', only importing existing values etc).</p>" +
                    "<p>A full list of special instructions can be found in the manual</p>");

            importStage.setTitle(importStage.getStageName());


        }


        toReturn.add(importStage);

        return toReturn;
    }


    private static void clearPreprocessorOutput(WizardInfo wizardInfo, String toBeCleared){
        try {
            Book book = wizardInfo.getPreprocessor();
            Range range = BookUtils.getFrontSheetRange(book, AZOUTPUT + toBeCleared);
            if (range != null) {
                range.toRowRange().clearAll();
                book.getSheetAt(0).getInternalSheet().getCell(range.getRow() - 1, 0).clearValue();
            }
            book.getInternalBook().deleteName(BookUtils.getNameByName(AZOUTPUT + toBeCleared, book.getSheetAt(0)));
            wizardInfo.getExtraTemplateFields().remove(toBeCleared);
            wizardInfo.setCurrentTemplate(null);
        }catch (Exception e){
            //no template!
        }
    }


    private static void loadPreprocessor(LoggedInUser loggedInUser)throws Exception{
         ImportTemplate  importTemplate = ImportTemplateDAO.findForNameAndBusinessId(getDBName(loggedInUser) + " " + loggedInUser.getWizardInfo().getImportSchedule().getTemplateName() + " Preprocessor.xlsx", loggedInUser.getBusiness().getId());
         Book book = null;
        if (importTemplate==null) {
                book = Books.createBook("Default preprocessor.xlsx");
                book.getInternalBook().createSheet("Preprocessor");
                newRange(book,"az_input",6);
                newRange(book,AZOUTPUT,12);
        }else {

            book = Importers.getImporter().imports(new File(SpreadsheetService.getHomeDir() + ImportService.dbPath + loggedInUser.getBusinessDirectory() + ImportService.importTemplatesDir + importTemplate.getFilenameForDisk()), "Report name");
        }
        loggedInUser.getWizardInfo().setPreprocessor(book);

        SName outputName = BookUtils.getNameByName(AZOUTPUT,book.getSheetAt(0));
        SName templateOutputName = BookUtils.getNameByName(AZOUTPUT + loggedInUser.getWizardInfo().getImportSchedule().getTemplateName(), book.getSheetAt(0));
        if (templateOutputName == null) {
            CellRegion outputRegion = outputName.getRefersToCellRegion();
            Ranges.range(book.getSheetAt(0), outputRegion.getRow(), outputRegion.getColumn(), outputRegion.getLastRow(), outputRegion.getLastColumn()).createName(AZOUTPUT + loggedInUser.getWizardInfo().getImportSchedule().getTemplateName());
        }
    }

    private static void savePreprocessor(LoggedInUser loggedInUser)throws Exception{
        Exporter exporter = Exporters.getExporter();
        String preprocessorName = getDBName(loggedInUser) + " " + loggedInUser.getWizardInfo().getImportSchedule().getTemplateName() + " Preprocessor.xlsx";
        ImportTemplate importTemplate = ImportTemplateDAO.findForNameAndBusinessId(preprocessorName, loggedInUser.getBusiness().getId());

        if (importTemplate!=null){
            File file = new File(SpreadsheetService.getHomeDir() + ImportService.dbPath + loggedInUser.getBusinessDirectory() + ImportService.importTemplatesDir + importTemplate.getFilenameForDisk());
            try (FileOutputStream fos = new FileOutputStream(file)) {
                exporter.export(loggedInUser.getWizardInfo().getPreprocessor(), fos);
            }
            return;
        }
        File file = File.createTempFile(Long.toString(System.currentTimeMillis()), "temp");
        try (FileOutputStream fos = new FileOutputStream(file)) {
            exporter.export(loggedInUser.getWizardInfo().getPreprocessor(), fos);
        }
        List<String> fileNames = new ArrayList<>();
        fileNames.add(preprocessorName);
        UploadedFile uploadedFile = new UploadedFile(file.getPath(),fileNames,false);
        ImportService.uploadImportTemplate(uploadedFile,loggedInUser, "uploaded automatically");

    }

    private static String getDBName(LoggedInUser loggedInUser){
        if (loggedInUser.getDatabase()!=null){
            return loggedInUser.getDatabase().getName();
        }
        return "unassigned";
    }

    private static void newRange(Book book, String rangeName, int row){
        Range newRange = Ranges.range(book.getSheetAt(0), row, 0, row + 1,0).toRowRange(); // insert at the 3rd row - should be rows to add - 1 as it starts at one without adding anything
        newRange.createName(rangeName);
        io.keikai.api.model.CellStyle cellStyle = newRange.getCellStyle();
        CellOperationUtil.applyBackColor(newRange,"#cccccc");

    }

    public static void preparePreprocessor(LoggedInUser loggedInUser, io.keikai.api.model.Book book)throws Exception{



        if (book==null){
            return;
        }
        io.keikai.api.model.Sheet sheet = book.getSheetAt(0);
        WizardInfo wizardInfo = loggedInUser.getWizardInfo();
         if (wizardInfo.getMatchFields()==null){
            return;
        }
         //check that we have data showing, and that all outputs are loaded....
         for (String field:wizardInfo.getMatchFields().keySet()){
             WizardField wizardField = wizardInfo.getMatchFields().get(field);
             if (wizardField.getValueFound() == null && wizardField.getValuesFound().size() > 0){
                 ImportUtils.getDataValuesFromFile(loggedInUser.getWizardInfo(), field,wizardField.getValuesFound().get(0));

             }
        }
         String currentTemplate = wizardInfo.getCurrentTemplate();
        wizardInfo.getExtraTemplateFields().put(wizardInfo.getCurrentTemplate(),wizardInfo.getFields());
        for (SName name:book.getInternalBook().getNames()){
            if (name.getName().toLowerCase().startsWith(AZOUTPUT.toLowerCase(Locale.ROOT))){
                String templatePage = name.getName().substring(AZOUTPUT.length());
                if (templatePage.length()> 0 && wizardInfo.getExtraTemplateFields().get(templatePage)==null){
                    wizardInfo.setCurrentTemplate(templatePage);
                    interpretTemplateData(loggedInUser, importTemplateData.getSheets().get(templatePage));
                    wizardInfo.getExtraTemplateFields().put(wizardInfo.getCurrentTemplate(),wizardInfo.getFields());
                }
            }
        }
        wizardInfo.setCurrentTemplate(currentTemplate);
        wizardInfo.setFields(wizardInfo.getExtraTemplateFields().get(currentTemplate));

        SName inputRegion = BookUtils.getNameByName("az_Input", sheet);
        int inputRowNo = inputRegion.getRefersToCellRegion().getRow();
        Map<String,SName> outputRegions = new HashMap<>();
        Map<String, Integer> outputRowNos = new HashMap<>();
        int maxOutputRow = 0;
        for (String template:wizardInfo.getExtraTemplateFields().keySet()){
            SName outputRegion = BookUtils.getNameByName(AZOUTPUT + template, sheet);
            if (outputRegion!=null){
                int outputRowNo = outputRegion.getRefersToCellRegion().getRow();
                outputRegions.put(template, outputRegion);
                outputRowNos.put(template,outputRowNo);
                if (outputRowNo > maxOutputRow){
                    maxOutputRow = outputRowNo;
                }
            }else{
                outputRowNos.put(template,0);
            }
        }
        for (String template:wizardInfo.getExtraTemplateFields().keySet()){
            if (outputRowNos.get(template)==0){
                SName outputName = BookUtils.getNameByName(AZOUTPUT,book.getSheetAt(0));
                CellRegion outputRegion = outputName.getRefersToCellRegion();
                Range insertRange = Ranges.range(sheet, maxOutputRow+2, 0, maxOutputRow + 5, 0).toRowRange(); // insert at the 3rd row - should be rows to add - 1 as it starts at one without adding anything
                CellOperationUtil.insertRow(insertRange);
                insertRange.clearAll();
                maxOutputRow +=4;
                Ranges.range(book.getSheetAt(0), maxOutputRow, 0, maxOutputRow + 1, 0).toRowRange().createName(AZOUTPUT + template);
                SName newName = BookUtils.getNameByName(AZOUTPUT + template, book.getSheetAt(0));
                Range copySource = Ranges.range(sheet, outputRegion.getRow(), 0, outputRegion.getRow() + 1, 0).toRowRange();
                Range targetRange = Ranges.range(sheet, maxOutputRow, 0, maxOutputRow + 1, 0).toRowRange();
                CellOperationUtil.pasteSpecial(copySource, targetRange, Range.PasteType.FORMATS, Range.PasteOperation.NONE,false, false);
                outputRowNos.put(template,maxOutputRow);
                outputRegions.put(template,newName);
            }
        }
        int sourceCol = 0;
        for (String field:wizardInfo.getMatchFields().keySet()){
            WizardField wizardField = wizardInfo.getMatchFields().get(field);
            ImportUtils.setKeikaiCell(sheet,inputRowNo,sourceCol, wizardField.getImportedName());
            ImportUtils.setKeikaiCell(sheet,inputRowNo + 1, sourceCol, wizardField.getValueFound());
            sourceCol++;

        }
         for (String template:outputRegions.keySet()) {
             int targetCol = 0;
             Map<String,WizardField>wizardFields = wizardInfo.getExtraTemplateFields().get(template);
            int outputRowNo = outputRowNos.get(template);
            ImportUtils.setKeikaiCell(sheet,outputRowNo - 1,0, "Output to " + template);
            for (String field : wizardFields.keySet()) {
                WizardField wizardField = wizardFields.get(field);
                ImportUtils.setKeikaiCell(sheet, outputRowNo, targetCol, wizardField.getImportedName());

                SCell targetCell = sheet.getInternalSheet().getCell(outputRowNo + 1, targetCol);
                if (targetCell!=null && targetCell.getType()==SCell.CellType.FORMULA){
                    //clear old auto-formulae
                    for(sourceCol = 0;sourceCol <wizardInfo.getMatchFields().size(); sourceCol++){
                        if (targetCell.getFormulaValue().equals(autoFormula(inputRowNo, sourceCol))){
                            targetCell.setValue(null);
                            break;
                        };
                    }
                }

                if (wizardField.getMatchedFieldName() == null) {
                      if (BookUtils.getValueAsString(targetCell).length() > 0
                            || targetCell.getType().equals(SCell.CellType.FORMULA)) {

                        wizardField.setMatchedFieldName(SPREADSHEETCALCULATION);
                    }
                }

                if (wizardField.getMatchedFieldName() != null && !SPREADSHEETCALCULATION.equals(wizardField.getMatchedFieldName())) {
                    sourceCol = ImportUtils.findColNo(wizardInfo.getMatchFields(), wizardField.getMatchedFieldName());
                    if (sourceCol >=0) {
                        targetCell.setFormulaValue(autoFormula(inputRowNo,sourceCol));
                    }
                }
                targetCol++;

            }
        }
    }

    private static String autoFormula(int sourceRow, int sourceCol){
        return "if(not(isblank(" + ImportUtils.cellAsString(sourceRow + 1, sourceCol) + "))," + ImportUtils.cellAsString(sourceRow + 1, sourceCol) + ",\"\")";

    }

    private static List<ImportWizardField> createMatchList(WizardInfo wizardInfo){
        List<ImportWizardField>toReturn = new ArrayList<>();
        Set<String>importHeadings = new LinkedHashSet<>();
        try{
            for (String matchField:wizardInfo.getFields().keySet()){
                importHeadings.add(choiceName(wizardInfo.getFields().get(matchField)));
            }
            for (String field:wizardInfo.getFields().keySet()){
                WizardField wizardField = wizardInfo.getFields().get(field);
                if (!SPREADSHEETCALCULATION.equals(wizardField.getMatchedFieldName()) && wizardField.getValuesFound()!=null && wizardField.getValuesFound().size()>0){
                    wizardField.setValueFound(wizardInfo.getMatchFields().get(wizardField.getMatchedFieldName()).getValueFound());
                }
            }
            Map<String,String> allMatches = getAllMatches(wizardInfo);

            for (String field:wizardInfo.getMatchFields().keySet()) {
                WizardField wizardField = wizardInfo.getMatchFields().get(field);
                String selected = allMatches.get(field);
                ImportWizardField importWizardField = new ImportWizardField(wizardField.getName());
                List<String> importChoice = new ArrayList<>(importHeadings);

                for (String testHeading:allMatches.keySet()){
                    if (!testHeading.equals(field) && allMatches.get(testHeading)!=null){
                        importChoice.remove(allMatches.get(testHeading));
                    }
                }
                List<String> listEntry = new ArrayList<>();
                listEntry.add("");
                boolean thisTemplate = false;
                for (String choice:importChoice){
                    if (choice.equals(selected)){
                        thisTemplate=true;
                        listEntry.add(choice + " selected");
                    }else
                        listEntry.add(choice);
                }
                importWizardField.setFieldName(field);//note that the 'humanized' names should not be used here.
                importWizardField.setValuesFound(get100(wizardField.getValuesFound(), wizardField.getValueFound()));
                if (!thisTemplate && selected!=null && selected.length()>0){
                    importWizardField.setListEntry(Collections.singletonList(selected));
                }else{
                    importWizardField.setListEntry(listEntry);
                }
                toReturn.add(importWizardField);
            }
        }catch(Exception e){
            int k=1;
        }
        return toReturn;
    }

    private static Map<String,String> getAllMatches(WizardInfo wizardInfo){
        Map<String,String> toReturn = new HashMap<>();
        for (String template:wizardInfo.getExtraTemplateFields().keySet()){
            toReturn.putAll(getMatches(wizardInfo.getExtraTemplateFields().get(template)));

        }
        toReturn.putAll(getMatches(wizardInfo.getFields()));
        return toReturn;
    }

    private static Map<String,String>getMatches(Map<String,WizardField> fields){
        Map<String,String>toReturn = new HashMap<>();
        for (String field:fields.keySet()){
            String matchfield = fields.get(field).getMatchedFieldName();
            if (matchfield!=null){
                if (KEYFIELDID.equals(fields.get(field).getType())){
                    toReturn.put(matchfield,fields.get(field).getName() + IDSUFFIX);
                }else{
                    toReturn.put(matchfield,fields.get(field).getName());

                }

            }
        }
        return toReturn;
    }

    private static String choiceName(WizardField wizardField){
        if (KEYFIELDID.equals(wizardField.getType())&& (wizardField.getSpecialInstructions()==null || !wizardField.getSpecialInstructions().contains("="))){
            return wizardField.getName() + IDSUFFIX;
        }
        return wizardField.getName();
    }
    private static List<ImportWizardField> createWizardFieldList(LoggedInUser loggedInUser, int stage, String chosenField, String chosenValue) {
        Map<String, WizardField> wizardFields = loggedInUser.getWizardInfo().getFields();
        List<ImportWizardField> toReturn = new ArrayList<>();
        List<String> peers = new ArrayList<>();
        String dataParent = loggedInUser.getWizardInfo().getImportSchedule().getTemplateName() + " Values";
        for (String field : wizardFields.keySet()) {
            WizardField wizardField = wizardFields.get(field);
            if (dataParent.equalsIgnoreCase(wizardField.getParent())) {
                peers = wizardField.getPeers();
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
                            if (KEYFIELDID.equals(wizardField.getType()) || peers.contains(field)) {
                                if(KEYFIELDID.equals(wizardField.getType())){
                                    importWizardField.setExample(KEYFIELDID);
                                }else{
                                    importWizardField.setExample("peer for data values");
                                }
                                importWizardField.setListEntry(possibleChildren);
                                importWizardField.setList2Entry(possibleAnchors);
                                break;
                            }

                            String currentChild = null;
                            String childValue = null;
                            if (possibleParents.contains(field) && wizardField.getAnchor() == null) {
                                //check we have some sample values
                                if (wizardField.getValuesFound()!=null && wizardField.getValueFound().length() == 0) {
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
                                    if (child.getParent()==null) {
                                        if (child.getName().equals(currentChild)) {
                                            possibleChildren.add(child.getName() + " selected");
                                        } else {
                                            possibleChildren.add(child.getName());
                                        }
                                    }
                                }
                            }

                            String currentAnchor = null;

                            if (!KEYFIELDID.equals(wizardField.getType()) && wizardField.getChild() == null && wizardField.getParent() == null) {
                                if (wizardField.getAnchor() != null) {
                                    WizardField anchorField = wizardFields.get(wizardField.getAnchor());
                                    String anchorValue = getChildValue(wizardFields, anchorField);
                                    importWizardField.setExample("e.g. " + wizardField.getValueFound() + " is an attribute of " + anchorValue);
                                    currentAnchor = wizardFields.get(wizardField.getAnchor()).getName();
                                }
                                for (String an : wizardFields.keySet()) {
                                    WizardField anchor = wizardFields.get(an);
                                    if (KEYFIELDID.equals(anchor.getType()) || anchor.getChild() != null || peers.contains(an)) {

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
                            importWizardField.setValuesFound(get100(wizardField.getValuesFound(), wizardField.getValueFound()));
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

    public static String errorToJson(String error){
        while (error.startsWith("error:")){
            error = error.substring(6);
        }
        return "{\"error\":\"" + error + "\"}";
    }

    public static String handleRequest(LoggedInUser loggedInUser, HttpServletRequest request) {


        servletContext = request.getServletContext();


        WizardInfo wizardInfo = loggedInUser.getWizardInfo();
        final ObjectMapper jacksonMapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        jacksonMapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
        String chosenField = request.getParameter("chosenfield");
        String chosenValue = request.getParameter("chosenvalue");
        int stage = Integer.parseInt(request.getParameter("stage"));
        int nextStage = Integer.parseInt(request.getParameter("nextstage"));
        String fieldsInfo = request.getParameter("fields");
        String template = request.getParameter("template");
        String cancelledTemplate = request.getParameter("canceltemplate");
        if (wizardInfo.getMatchFields()!=null){
            if (template==null || template.length()==0) {
                template = wizardInfo.getImportSchedule().getTemplateName();
            }
            if (cancelledTemplate!=null && cancelledTemplate.length()>0){
                clearPreprocessorOutput(wizardInfo,cancelledTemplate);
            }

            if (template!=null && template.length()> 0 && !template.equals(wizardInfo.getCurrentTemplate())){
                Map<String,Map<String,WizardField>> extraTemplateFields = wizardInfo.getExtraTemplateFields();
                if (wizardInfo.getCurrentTemplate()!=null){
                    extraTemplateFields.put(wizardInfo.getCurrentTemplate(), wizardInfo.getFields());
                }
                Map<String, WizardField> newFields = extraTemplateFields.get(template);
                if (newFields==null){
                    interpretTemplateData(loggedInUser, importTemplateData.getSheets().get(template));

                }else{
                    wizardInfo.setFields(newFields);
                }
                wizardInfo.setCurrentTemplate(template);
            }
            if("spreadsheet calculate".equals(chosenField)) {
                int lines = wizardInfo.getLineCount();
                if (lines > 100) {
                    lines = 100;
                }
                calcSpreadsheetData(loggedInUser, lines);
            }

            nextStage = MATCHSTAGE;
            stage = MATCHSTAGE;

        }

        if (stage!=NAMESTAGE && chosenField!=null && chosenField.length()>0){
            chosenField = ImportUtils.findFieldFromName(wizardInfo,chosenField, wizardInfo.getMatchFields()!=null);
        }

        if(chosenValue==null || chosenValue.length()==0){
            String error = processFound(wizardInfo, fieldsInfo, stage);
            if (error != null) {
                return errorToJson(error);
            }
        }
        stage = nextStage;
        try {
            Map<String, List<ImportStage>> output = new HashMap<>();
            List<ImportStage> stages = createStageList(nextStage);
            if (nextStage!=MATCHSTAGE) {
                if (nextStage > wizardInfo.getMaxStageReached()) {
                    String suggestion = ImportSuggestion.makeSuggestion(wizardInfo, nextStage);
                    if (suggestion.length() > 1) {
                        stages.get(stage - 1).setSuggestions("<p>" + suggestion + "</p><p>You can override these suggestions:</p>");
                    }

                    wizardInfo.setMaxStageReached(stage);
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
            }else{
                wizardFields = createWizardFieldList(loggedInUser, nextStage, chosenField, chosenValue);
            }
            Map<String, List<ImportWizardField>> fields = new LinkedHashMap<>();
            fields.put("field", wizardFields);
            String fieldInfo = jacksonMapper.writeValueAsString(fields);
            toReturn += fieldInfo;
            Map<String,List<String>> templateMap=new HashMap<>();
            List<String>templates = new ArrayList<>();
            String origTemplate = wizardInfo.getImportSchedule().getTemplateName();
            templates.add(origTemplate);
            for (String template1:importTemplateData.getSheets().keySet()){
                if (!template1.equals(origTemplate)) {
                     if (template1.equals(template)) {
                        templates.add(template1 + " selected");
                    } else {
                        templates.add(template1);
                    }
                }
            }
            templateMap.put("template",templates);
            toReturn+=jacksonMapper.writeValueAsString(templateMap);
            return toReturn.replace("}{", ",");//not proud of this!


        } catch (Exception e) {
            return errorToJson("error:" + e.getMessage());
        }
    }

    private static void calcSpreadsheetData(LoggedInUser loggedInUser, int lines){
        WizardInfo wizardInfo = loggedInUser.getWizardInfo();
        Book book = wizardInfo.getPreprocessor();
        io.keikai.api.model.Sheet sheet = book.getSheetAt(0);
        SName inputRegion = BookUtils.getNameByName("az_Input", sheet);
        int inputRow = inputRegion.getRefersToCellRegion().getRow() + 1;
        SName outputRegion = BookUtils.getNameByName(AZOUTPUT, sheet);
        int outputRow = outputRegion.getRefersToCellRegion().getRow() + 1;
        boolean hasCalc = false;
        int count=0;
        for (String field:wizardInfo.getFields().keySet()) {
            WizardField wizardField = wizardInfo.getFields().get(field);
            if (wizardField.getMatchedFieldName()==null || SPREADSHEETCALCULATION.equals(wizardField.getMatchedFieldName())){
                if (BookUtils.getValueAsString(sheet.getInternalSheet().getCell(outputRow,count)).length()>0
                        || sheet.getInternalSheet().getCell(outputRow,count).getType().equals(SCell.CellType.FORMULA)){

                    hasCalc = true;
                    wizardField.setMatchedFieldName(SPREADSHEETCALCULATION);
                }else{
                    wizardField.setMatchedFieldName(null);
                }
                wizardField.setValuesFound(new ArrayList<>());
            }
            count++;
        }
        if (!hasCalc){
            return;
        }

        Map<String, WizardField> sourceFields = wizardInfo.getMatchFields();
        for (int line = 0; line < lines;line++){
            count = 0;
            for (String field:sourceFields.keySet()){
                String valueFound = "";
                List<String> valuesFound = sourceFields.get(field).getValuesFound();
                if (valuesFound!=null && valuesFound.size()> line){
                    valueFound = valuesFound.get(line);
                }
                ImportUtils.setKeikaiCell(sheet,inputRow,count,valueFound);
                count++;
            }
            Range selection = Ranges.range(sheet, inputRow,0,inputRow, count -1);
            selection.notifyChange();
            count=0;
            for (String field:wizardInfo.getFields().keySet()){
                WizardField wizardField = wizardInfo.getFields().get(field);
                if (SPREADSHEETCALCULATION.equals(wizardField.getMatchedFieldName())){
                    wizardField.getValuesFound().add(BookUtils.getValueAsString(sheet.getInternalSheet().getCell(outputRow,count)));
                }
                count++;

            }
        }
    }


    public static String setTemplate(LoggedInUser loggedInUser){
        try {
            WizardInfo wizardInfo = loggedInUser.getWizardInfo();
            ImportTemplate importTemplate = null;
             if (loggedInUser.getDatabase() !=null) {
                int importTemplateId = loggedInUser.getDatabase().getImportTemplateId();
                if (importTemplateId > 0) {
                    importTemplate = ImportTemplateDAO.findById(importTemplateId);
                }
            }
            if (importTemplate==null){
                String templateName = getDBName(loggedInUser) + " Import Templates.xlsx";
                importTemplate = ImportTemplateDAO.findForName(templateName);
                if (importTemplate==null) {
                    Workbook book = new XSSFWorkbook();
                    String tempPath = SpreadsheetService.getHomeDir() + "/temp/"+ templateName;
                    File tempLocation = new File(tempPath);
                    tempLocation.delete();
                    OutputStream os = new FileOutputStream(tempLocation);
                    book.write(os);
                    UploadedFile uploadedFile = new UploadedFile(tempPath, Collections.singletonList(templateName), false);
                    ImportService.uploadImportTemplate(uploadedFile,loggedInUser, "created automatically");
                    importTemplate = ImportTemplateDAO.findForName(templateName);
                }

            }
            wizardInfo.setImportTemplate(importTemplate);
            importTemplateData = ImportService.getImportTemplateData(importTemplate, loggedInUser);
            Workbook book =  loadBookAndClearTemplate(loggedInUser);
            String templateName = wizardInfo.getImportSchedule().getTemplateName();
            Sheet sheet = book.createSheet(templateName); // literals not best practice, could it be factored between this and the xlsx file?
            int rowNo = 0;
            List<List<String>>templateContents = importTemplateData.getSheets().get(templateName);
            if (templateContents!=null) {
                for (List<String> row : templateContents) {
                    int colNo = 0;
                    for (String cell : row) {
                        if (cell != null && cell.length() > 0) {
                            Preprocessor.setCellValue(sheet, rowNo, colNo++, cell);
                        }else{
                            colNo++;
                        }
                    }
                    rowNo++;
                }
            }
            if (rowNo < 8){
                rowNo=8;
            }
            Preprocessor.setCellValue(sheet, rowNo++, 0, "PARAMETERS");
            for (String templateParam:wizardInfo.getTemplateParameters().keySet()){
                Preprocessor.setCellValue(sheet, rowNo, 0, templateParam);
                Preprocessor.setCellValue(sheet,rowNo++,1, wizardInfo.getTemplateParameters().get(templateParam));
            }
            String filePath = SpreadsheetService.getHomeDir() + ImportService.dbPath + loggedInUser.getBusinessDirectory() + ImportService.importTemplatesDir + importTemplate.getFilenameForDisk();

            FileOutputStream out = new FileOutputStream(filePath);
            book.write(out);
            out.close();
            interpretTemplateData(loggedInUser, importTemplateData.getSheets().get(templateName));
            return uploadTheFile(loggedInUser, wizardInfo.getImportFile(), importTemplateData);

        }catch(Exception e){
            return "error:" + e.getMessage();
        }

    }


    public static String uploadTheFile(LoggedInUser loggedInUser, UploadedFile uploadFile, ImportTemplateData importTemplateData) {
         try {
             WizardInfo targetInfo = loggedInUser.getWizardInfo();
             WizardInfo sourceInfo = null;
             if (targetInfo.getImportSchedule().getConnectorId() > 0){
                 sourceInfo = readSQL(loggedInUser,targetInfo);
             }else {
                 UploadedFile uploadedFile = new UploadedFile(uploadFile.getPath(), Collections.singletonList(uploadFile.getFileName()), false);
                 sourceInfo = new WizardInfo(uploadedFile, null, null);

                 String fileName = uploadFile.getFileName();
                 if (fileName.toLowerCase(Locale.ROOT).contains(".xls")) {
                     Map<String, WizardField> wizardFields = new LinkedHashMap<>();
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

                     sourceInfo = new WizardInfo(uploadedFile, null, jsonObject);
                     targetInfo.setImportFileData(jsonObject);
                 } else {
                     Map<String, String> fileNameParameters = new HashMap<>();
                     ImportService.addFileNameParametersToMap(uploadFile.getFileName(), fileNameParameters);
                     sourceInfo = new WizardInfo(uploadedFile, null, null);
                     Map<String, WizardField> wizardFields;
                     sourceInfo = readCSVFile(uploadFile.getPath(), fileNameParameters.get("fileencoding"), sourceInfo);
                     //wizardInfo.setFields(wizardFields);
                     for (String field : sourceInfo.getFields().keySet()) {
                         WizardField wizardField = sourceInfo.getFields().get(field);
                         sourceInfo.setLineCount(wizardField.getValuesFound().size());
                         break;
                     }
                 }
                 for (String field : sourceInfo.getFields().keySet()) {
                     WizardField wizardField = sourceInfo.getFields().get(field);
                     String sansId = ImportUtils.removeId(wizardField.getName());
                     if (!sansId.equals(wizardField.getName()) && !usedName(sourceInfo, sansId)) {
                         wizardField.setName(sansId);

                     }
                 }
             }
             loadPreprocessor(loggedInUser);
             if (targetInfo.getFields().size() > 0) {
                targetInfo.setMaxStageReached(SPECIALSTAGE);
                for (String field : sourceInfo.getFields().keySet()) {
                    WizardField source = sourceInfo.getFields().get(field);
                    WizardField target = targetInfo.getFields().get(ImportUtils.standardise(field));
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
                        ImportUtils.calcXL(loggedInUser.getWizardInfo(), 100);

                        return "importwizard";
                    } else {
                        target.setValuesFound(source.getValuesFound());
                        ImportUtils.setDistinctCount(target);
                    }
                }
                targetInfo.setLineCount(sourceInfo.getLineCount());
                setupValueFound(targetInfo);

            } else {
                targetInfo.setFields(sourceInfo.getFields());
                targetInfo.setLineCount(sourceInfo.getLineCount());
            }
            ImportUtils.calcXL(loggedInUser.getWizardInfo(), 100);

        } catch (Exception e) {
             int k = 1;
        }
        return "importwizard";

    }

    private static void matchTheField(WizardInfo wizardInfo, WizardField targetField, String sourceName) {
        if (sourceName!=null) {
            WizardField sourceField = wizardInfo.getMatchFields().get(sourceName);
            targetField.setMatchedFieldName(ImportUtils.standardise(sourceField.getImportedName()));
            sourceField.setMatchedFieldName(targetField.getMatchedFieldName());
            targetField.setValuesFound(sourceField.getValuesFound());
            targetField.setDistinctCount(sourceField.getDistinctCount());
        }else{
            if (!SPREADSHEETCALCULATION.equals(targetField.getMatchedFieldName())) {
                targetField.setMatchedFieldName(null);
                targetField.setValuesFound(null);
                targetField.setDistinctCount(0);
                targetField.setValueFound(null);
            }
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

    private static void createBasicReport(LoggedInUser loggedInUser)throws  Exception{
        WizardInfo wizardInfo= loggedInUser.getWizardInfo();
        boolean hasData = false;
        boolean hasKeyField = false;
        for (String field:wizardInfo.getFields().keySet()){
            if (wizardInfo.getFields().get(field).getParent()!=null){
                hasData = true;
            }
            if (KEYFIELDID.equals(wizardInfo.getFields().get(field).getType())){
                hasKeyField=true;
            }
        }
        if (!hasData || !hasKeyField){
            return;
        }
        String templateName = wizardInfo.getImportSchedule().getTemplateName();
        Book book = Importers.getImporter().imports(servletContext.getResourceAsStream("/WEB-INF/BasicReportTemplate.xlsx"), "Report name");
        BookUtils.setNameValue(book,"az_ReportName", templateName + " Import");
        BookUtils.setNameValue(book,"az_ImportChoice", "`" + templateName + " Imports` children");
        BookUtils.setNameValue(book,"az_RowHeadingsData","`[import]` children sorted");
        SName region = BookUtils.getNameByName("az_ColumnHeadingsData", book.getSheetAt(0));
        SSheet sheet = book.getSheetAt(0).getInternalSheet();

        int row = region.getRefersToCellRegion().getRow();
        int firstCol = region.getRefersToCellRegion().getColumn();
        //region.setRefersToFormula(region.getSheetName()+"!"+ ImportUtils.cellAsString(row,firstCol) +":" + ImportUtils.cellAsString(row, firstCol + basicReportColHeadings.size() - 1));
        int col = firstCol;
        for (String field:wizardInfo.getFields().keySet()) {
            String colHeading = null;
            WizardField wizardField = wizardInfo.getFields().get(field);
            if (wizardField.getSelect()) {
                if (KEYFIELDID.equals(wizardField.getType())){
                    colHeading = "." + wizardField.getImportedName();
                }else if (KEYFIELDNAME.equals(wizardField.getType())) {
                    colHeading = ".default_display_name";
                }else if(wizardField.getAnchor() != null||wizardField.getChild()!=null) {
                    colHeading ="." + wizardField.getName();
                }else{
                    colHeading = wizardField.getName();
                }
                BookUtils.setValue(book.getSheetAt(0).getInternalSheet().getCell(row, col), colHeading);
                BookUtils.setValue(sheet.getCell(row+1, col++), wizardField.getName());

            }
        }
        SName dataRegion = BookUtils.getNameByName("az_DataRegionData",book.getSheetAt(0));

        String tempPath = SpreadsheetService.getHomeDir() + "/temp/" + System.currentTimeMillis() + templateName+"ReportTemplate.xlsx"; // timestamp to stop file overwriting
        File tempLocation = new File(tempPath);
        tempLocation.delete();
        FileOutputStream os = new FileOutputStream(tempLocation);
        Exporter exporter = Exporters.getExporter();
        exporter.export(book,os);
        os.close();
        UploadedFile uf = new UploadedFile(tempPath,Collections.singletonList("Basic Report Template.xlsx"), false);
        ImportService.uploadReport(loggedInUser, templateName + " Import", uf);



    }



    private static void uploadData(LoggedInUser loggedInUser, Map<String, List<String>> data) throws Exception {


        Map<String, String> wizardDefs = new LinkedHashMap<>();
        WizardInfo wizardInfo = loggedInUser.getWizardInfo();
        for (String field : wizardInfo.getFields().keySet()) {
            WizardField wizardField = wizardInfo.getFields().get(field);
            setInterpretation(wizardInfo,wizardField);
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
                if (clauses[firstClause].equals(KEYFIELDID)) {
                    keyFieldId = clauses[nameRow];
                }
            }
        }

        for (String field : wizardDefs.keySet()) {
            //this conversion is complicated by the fact that we do not want the 'id' fields to be names, but do want them to continue as attributes( 'product id' is attribute of 'product')

            String wizardDef = wizardDefs.get(field);
            WizardField wizardField = wizardInfo.getFields().get(field);
            String[] clauses = wizardDef.split(";");
            StringBuffer modifiedHeading = new StringBuffer();
            if (clauses.length > firstClause) {
                String fieldName = clauses[nameRow];
                String dataAnchor = getClause("attribute of", clauses);
                if (dataAnchor != null) {
                    modifiedHeading = new StringBuffer();
                    if (ImportUtils.isIdField(wizardInfo, wizardField.getAnchor()) && fieldName.toLowerCase(Locale.ROOT).endsWith("name")) {
                        fieldName = "name";
                    }
                    modifiedHeading.append(dataAnchor + ";attribute " + fieldName);
                } else {
                    modifiedHeading.append(fieldName);
                }
                if (clauses[firstClause].equals(StringLiterals.DATELANG) || clauses[firstClause].equals(StringLiterals.USDATELANG)) {
                    modifiedHeading.append(";datatype " + clauses[firstClause]);

                }
                if (clauses[firstClause].equals("key field name")) {
                    if (keyFieldId != null) {
                        modifiedHeading = new StringBuffer();
                        modifiedHeading.append(keyFieldId + ";attribute name");
                    } else {
                        modifiedHeading.append(";child of " + fieldName);

                    }
                }
                String templateName = loggedInUser.getWizardInfo().getImportSchedule().getTemplateName();
                if (clauses[firstClause].equals(KEYFIELDID) || ImportUtils.isIdField(wizardInfo, field)) {
                    modifiedHeading.append(";language " + fieldName + "id;child of " + fieldName);
                    if (KEYFIELDID.equals(wizardField.getType())){
                        CommonReportUtils.getDropdownListForQuery(loggedInUser,"edit:create Imports->" + templateName + " Imports->Import from " + wizardInfo.getImportFile().getFileName());

                        modifiedHeading.append(",Import from " + wizardInfo.getImportFile().getFileName());
                    }
                }
                String dataChild = getClause("parent of", clauses);
                if (dataChild != null) {
                    modifiedHeading.append(";parent of " + dataChild);
                    if (ImportUtils.areNumbers(wizardField.getValuesFound())){
                        String langClause = ";language " + fieldName + "id";
                        if (!modifiedHeading.toString().contains(langClause)){
                            modifiedHeading.append(langClause);
                        }
                    }
                    String specialInstructions = wizardField.getSpecialInstructions().toLowerCase(Locale.ROOT);
                    if (specialInstructions==null || !(specialInstructions.startsWith("=") || specialInstructions.contains(";=")) ||specialInstructions.contains("composition")){
                        modifiedHeading.append(";default No " + fieldName);
                    }
                    String childClause = ";child of " + fieldName;
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
                    modifiedHeading.append(";peers " + peersString);
                }
                String peerFor = getClause("peer for", clauses);
                if (peerFor != null) {
                    String clause = ";child of " + fieldName;
                    if (!modifiedHeading.toString().contains(clause)) {
                        modifiedHeading.append(";child of " + fieldName);
                    }
                }
                String specialInstructions = wizardField.getSpecialInstructions();
                if (specialInstructions!=null && specialInstructions.length() > 0){
                    String[]specialClauses = specialInstructions.split(";");
                    for (String specialClause:specialClauses){
                        if (specialClause.startsWith("=")){
                            modifiedHeading.append(";az=" + specialClause.substring(1));

                        }else{
                            if (!modifiedHeading.toString().contains(specialClause)){
                                modifiedHeading.append(";" + specialClause);
                            }
                        }
                    }
                }
                if (modifiedHeading.length() > 0) {
                    modifiedHeadings.put(field, clauses[0] + ";" + modifiedHeading.toString());
                }
            }else{
                modifiedHeadings.put(field,wizardDef);
            }
        }
        saveTemplate(loggedInUser, modifiedHeadings);
        if (wizardInfo.getMatchFields()!=null){
            return;
        }
        createBasicReport(loggedInUser);
        //now take off the fields to ignore.
        for (String field : wizardInfo.getFields().keySet()) {
            if (!wizardInfo.getFields().get(field).getSelect()) {
                modifiedHeadings.remove(field);
            }
        }


        RMIClient.getServerInterface(loggedInUser.getDatabaseServer().getIp()).uploadWizardData(loggedInUser.getDataAccessToken(), wizardInfo.getImportFile().getFileName(), modifiedHeadings, data);

    }

    private static void interpretTemplateData(LoggedInUser loggedInUser,  List<List<String>> templateData) {
        WizardInfo wizardInfo = loggedInUser.getWizardInfo();
        if (templateData == null) {
            templateData = new ArrayList<>();
        }
        wizardInfo.setFields(new LinkedHashMap<>());
        if (wizardInfo.getCurrentTemplate()==null){
            wizardInfo.setCurrentTemplate(wizardInfo.getImportSchedule().getTemplateName());
        }
        int rows = 0;
        while (rows < templateData.size() && templateData.get(rows).size() > 0) {
            rows++;
        }
        if (rows < 2) return;
        Map<String, WizardField> nameMap = new LinkedHashMap<>();
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
                            wizardField.setAnchor(wizardField.getName());//NOTE THIS IS TEMPORARY ONLY.  The Anchor should be the imported name - changed in InterpretClause
                            wizardField.setName(clause.substring(StringLiterals.ATTRIBUTE.length()).trim());//to stop the attribute being found in 'findField

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
        boolean topDefinitions = true;
        //needs a small amount of info that is not in the top definitions - copied further down the sheet
        for (List<String> row:templateData){
            if (row.size()==0){
                topDefinitions = false;
            }
            if (!topDefinitions && row.size()> 4){
                for (int col = 4;col < row.size();col++){
                    if (row.get(col).contains(";added")){
                        String field = row.get(col - 2);
                        if (field !=null && wizardInfo.getFields().get(field) != null){
                            wizardInfo.getFields().get(field).setAdded(true);
                        }
                    }
                    if (row.get(col).contains(KEYFIELDID)){
                        String field = row.get(col - 2);
                        if (field !=null && wizardInfo.getFields().get(field) != null){
                            WizardField wizardField = wizardInfo.getFields().get(field);
                            clearField(wizardField);
                            wizardField.setType(KEYFIELDID);
                         }
                    }
                    if (row.get(col).contains(KEYFIELDNAME)){
                        String field = row.get(col - 2);
                        if (field !=null && wizardInfo.getFields().get(field) != null){
                            WizardField wizardField = wizardInfo.getFields().get(field);
                            clearField(wizardField);
                            wizardField.setType(KEYFIELDNAME);

                        }
                    }
                }
            }

        }
    }

    private static  void clearField(WizardField wizardField){
        wizardField.setType(null);
        wizardField.setChild(null);
        wizardField.setParent(null);
        wizardField.setPeers(null);
        wizardField.setAnchor(null);

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
                                if (parent==null){
                                    parent = wizardInfo.getImportSchedule().getTemplateName() + " Values";
                                }
                                wizardField.setParent(parent);
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
                    String anchor = ImportUtils.findFieldFromName(wizardInfo, wizardField.getAnchor());
                    wizardField.setAnchor(anchor);
                    if (clauseData.equalsIgnoreCase("name")&& wizardField.getAnchor()!=null) {
                        clauseData = wizardInfo.getFields().get(wizardField.getAnchor()).getName() + " Name";
                        if (KEYFIELDID.equals(wizardInfo.getFields().get(anchor).getType())) {
                            wizardField.setType(KEYFIELDNAME);
                        }

                    }
                    wizardField.setName(clauseData);
                } else if (clause.startsWith(StringLiterals.AZEQUALS)) {
                    addSpecialInstructions(wizardField, clause.substring(2));
                } else if (clause.startsWith(StringLiterals.DATATYPE)) {
                    wizardField.setType(clauseData);
                } else if (clause.startsWith(StringLiterals.LANGUAGE) && clauseData.startsWith(wizardField.getName())) {
                    //ignore

                } else if (clause.startsWith(StringLiterals.CHILDOF)) {
                    clauseData = clause.substring(StringLiterals.CHILDOF.length()).trim();
                    String[] parents = clauseData.split(",");
                    if (!wizardField.getName().startsWith(parents[0])) {
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
        if (clause.startsWith("az=")){
            clause = clause.substring(2);
        }
        wizardField.setSpecialInstructions(specialInstructions + clause);

    }

    private static Workbook loadBookAndClearTemplate(LoggedInUser loggedInUser)throws Exception{
        ImportTemplate importTemplate = loggedInUser.getWizardInfo().getImportTemplate();
        if (importTemplate == null){
            return new XSSFWorkbook();
        }
        String filePath = SpreadsheetService.getHomeDir() + ImportService.dbPath + loggedInUser.getBusinessDirectory() + ImportService.importTemplatesDir + importTemplate.getFilenameForDisk();
        InputStream in = new FileInputStream(filePath);
        Workbook book = new XSSFWorkbook(in);
        in.close();
        String templateName = loggedInUser.getWizardInfo().getImportSchedule().getTemplateName();
        for (int sheetNo = 0; sheetNo < book.getNumberOfSheets(); sheetNo++) {
            if (book.getSheetAt(sheetNo).getSheetName().equals(templateName)) {
                book.removeSheetAt(sheetNo);
                break;
            }
        }
        return book;

    }


    private static void saveTemplate(LoggedInUser loggedInUser, Map<String, String> modifieldHeadings) throws Exception {
        Workbook book =  loadBookAndClearTemplate(loggedInUser);
        String templateName = loggedInUser.getWizardInfo().getImportSchedule().getTemplateName();
        Sheet sheet = book.createSheet(templateName); // literals not best practice, could it be factored between this and the xlsx file?
        int row = 8;
        int col = 4;
        WizardInfo wizardInfo = loggedInUser.getWizardInfo();
        for (String field : wizardInfo.getFields().keySet()) {
            WizardField wizardField = wizardInfo.getFields().get(field);

            Preprocessor.setCellValue(sheet, row, col, ImportUtils.lookupList(field,wizardInfo.getLookups()));//a comma separated list
            Preprocessor.setCellValue(sheet, row, col + 1, wizardField.getName());
            Preprocessor.setCellValue(sheet, row++, col + 2, wizardField.getInterpretation());
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
                Preprocessor.setCellValue(sheet, rowNo++, col, clause);
            }
            col++;

            //Preprocessor.setCellValue(sheet,0, col++, modifieldHeadings.get(field));
        }
        int rowNo = 8;
        wizardInfo.getTemplateParameters().put(ImportService.POSTPROCESSOR, "do " + wizardInfo.getImportSchedule().getTemplateName() + " Import");
        wizardInfo.getTemplateParameters().put("schema","withquotes");
        Preprocessor.setCellValue(sheet, rowNo++, 0, "PARAMETERS");
        for (String templateParam:wizardInfo.getTemplateParameters().keySet()){
            Preprocessor.setCellValue(sheet, rowNo, 0, templateParam);
            Preprocessor.setCellValue(sheet,rowNo++,1, wizardInfo.getTemplateParameters().get(templateParam));
        }
        String templateFileName = "unassigned import templates.xlsx";
        if (loggedInUser.getDatabase()!=null) {
            int importTemplateId = loggedInUser.getDatabase().getImportTemplateId();
            ImportTemplate importTemplate = ImportTemplateDAO.findById(importTemplateId);
            templateFileName = importTemplate.getFilenameForDisk();

        }
        String filePath = SpreadsheetService.getHomeDir() + ImportService.dbPath + loggedInUser.getBusinessDirectory() + ImportService.importTemplatesDir + templateFileName;

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
