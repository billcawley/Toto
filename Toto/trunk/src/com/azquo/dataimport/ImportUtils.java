package com.azquo.dataimport;

import com.azquo.DateUtils;
import com.azquo.StringLiterals;
import com.azquo.spreadsheet.LoggedInUser;
import com.azquo.spreadsheet.SpreadsheetService;
import com.azquo.spreadsheet.transport.UploadedFile;
import com.azquo.spreadsheet.zk.BookUtils;
import io.keikai.api.model.Sheet;
import io.keikai.model.SCell;
import org.apache.commons.lang.math.NumberUtils;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.AreaReference;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ImportUtils {

    static Workbook wb = new XSSFWorkbook();
    static Cell excelCell = wb.createSheet("new sheet").createRow(0).createCell(0);
    static final FormulaEvaluator formulaEvaluator = wb.getCreationHelper().createFormulaEvaluator();
    // EFC note - needed to add this to make it compile
    static final String DATELANG = "date";
    static final String USDATELANG = "us date";
    public static final String[] TYPEOPTIONS = {ImportWizard.KEYFIELDID, ImportWizard.KEYFIELDNAME, DATELANG, USDATELANG, "time", "datetime"};
    static final String[] idSuffixes = {"id","code","key","number","ref"};


    public static boolean isValidAnchor(WizardInfo wizardInfo, String fieldName) {
        WizardField anchor = wizardInfo.getFields().get(findFieldFromName(wizardInfo, fieldName));
        if (anchor.getParent() != null || anchor.getAnchor() != null) {
            return false;
        }
        return true;
    }

    public static boolean isKeyField(WizardField wizardField) {
        if (ImportWizard.KEYFIELDNAME.equals(wizardField.getType())) return true;
        if (ImportWizard.KEYFIELDID.equals(wizardField.getType())) return true;
        return false;
    }

    public static boolean isDate(WizardField wizardField) {
        if (wizardField.getType() != null && !isKeyField(wizardField)) {
            return true;
        }
        return false;
    }




    public static String findFieldFromName(WizardInfo wizardInfo, String fieldName){
        return findFieldFromName(wizardInfo,fieldName, false);
    }

    public static String findFieldFromName(WizardInfo wizardInfo, String fieldName, boolean isMatchFields) {
        Map<String,WizardField>fields = wizardInfo.getFields();
        if (isMatchFields){
            fields = wizardInfo.getMatchFields();
        }
        for (String field : fields.keySet()) {
            String fieldN = wizardInfo.getFields().get(field).getName();
            if (fieldN.equalsIgnoreCase(fieldName.trim())) {
                return field;
            }
            if (fieldN.equalsIgnoreCase(fieldName)) {
                return field;
            }

        }
        return null;
    }

    public static String HTMLify(String string) {
        return string.replace(" ", "_").replace("\"", "").replace("'", "").replace("`", "");
    }



    public static String removeId(String fieldName) {
        for (String idSuffix : idSuffixes) {
            if (fieldName.toLowerCase(Locale.ROOT).endsWith(idSuffix)) {
                return fieldName.substring(0, fieldName.length() - idSuffix.length()).trim();
            }
        }
        return fieldName;
    }



    public static String nameField(String idField) {
        String lc = idField.toLowerCase(Locale.ROOT);
        for (String suffix : idSuffixes) {
            if (lc.endsWith(suffix)) {
                return idField.substring(0, idField.length() - suffix.length()) + "Name";
            }
        }
        return null;

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
            Map<String,WizardField>fields = wizardInfo.getFields();
            if (wizardInfo.getMatchFields()!=null){
                fields = wizardInfo.getMatchFields();
            }
            if (testField != null && testField.length() > 0) {
                WizardField wizardField = fields.get(testField);
                for (int i = 0; i < wizardField.getValuesFound().size(); i++) {
                    if (wizardField.getValuesFound().get(i).equals(testItem)) {
                        for (String field : fields.keySet()) {
                            WizardField wizardField1 = fields.get(field);
                            if (wizardField1.getValuesFound()!=null && wizardField1.getValuesFound().size()>i){
                                wizardField1.setValueFound(wizardField1.getValuesFound().get(i));
                            }
                        }
                        break;
                    }
                }
            }
            return list;
        }

    }


    public static Map<String, WizardField> readJsonFile(WizardInfo wizardInfo, String testField, String testItem) throws Exception {
        Map<String, List<String>> fieldsFound = new LinkedHashMap<>();
        List<ImportWizard.JSONtest> jsonTests = new ArrayList<>();
        String error = createJSONTests(wizardInfo, jsonTests, testField, testItem);
        if (error != null) {
            throw new Exception(error);
        }
        if (testItem == null) {
            testItem = "*";
        }
        if (testField != null && testField.length() > 0 && !wizardInfo.getFields().get(testField).getAdded()) {
            testItem = ImportService.JSONFIELDDIVIDER + testField + ImportService.JSONFIELDDIVIDER + testItem;
        } else {
            testItem = "";
        }

        try {
            JSONObject jsonObject = wizardInfo.getImportFileData();//remove line feeds
            traverseJSON(fieldsFound, "", jsonObject, 10, testItem, jsonTests);
            if (wizardInfo.getFields().size() == 0) {
                Map<String, String> reverseNames = new HashMap<>();
                for (String field : fieldsFound.keySet()) {

                    String suggestedName = field;
                    try {
                        suggestedName = humaniseName(field.substring(field.lastIndexOf(ImportService.JSONFIELDDIVIDER) + 1));
                        String nameWithoutId = removeId(suggestedName);
                        if (reverseNames.get(nameWithoutId)==null){
                            suggestedName = nameWithoutId;
                        }
                        if (reverseNames.get(suggestedName) != null) { //if suggested names may be duplicates, grab a bit more string
                            String original = reverseNames.get(suggestedName);
                            String originalSuggestion = original.substring(nthLastIndexOf(2, ImportService.JSONFIELDDIVIDER, original) + 1);
                            originalSuggestion = humaniseName(originalSuggestion);
                            reverseNames.remove(suggestedName);
                            reverseNames.put(originalSuggestion, original);
                            wizardInfo.getFields().get(original).setName(originalSuggestion);
                            suggestedName = field.substring(nthLastIndexOf(2, ImportService.JSONFIELDDIVIDER, field) + 1);
                        }
                    } catch (Exception e) {
                    }
                    reverseNames.put(suggestedName, field);
                    WizardField wizardField = new WizardField(field, insertSpaces(suggestedName), false);
                    wizardField.setValuesFound(fieldsFound.get(field));
                    setDistinctCount(wizardField);
                    //adjustValuesFound(wizardField,fieldsFound.get(field));
                    wizardInfo.getFields().put(field, wizardField);
                }
            } else {
                if (testItem != null) {
                    for (String field : fieldsFound.keySet()) {
                        if (fieldsFound.get(field).size() > 0) {
                            wizardInfo.getFields().get(field).setValueFound(fieldsFound.get(field).get(0));
                        } else {
                            wizardInfo.getFields().get(field).setValueFound("");
                        }
                        //adjustValuesFound(wizardInfo.getFields().get(field),fieldsFound.get(field));
                    }
                }
            }
            Map<String, WizardField> toReturn = new LinkedHashMap<>();
            for (String field : fieldsFound.keySet()) {
                toReturn.put(headingFrom(field, wizardInfo.getLookups()), wizardInfo.getFields().get(field));
            }
            return toReturn;


        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static String createJSONTests(WizardInfo wizardInfo, List<ImportWizard.JSONtest> jsoNtests, String testField, String testValue) throws Exception {
        String error = null;
        for (String field : wizardInfo.getFields().keySet()) {
            WizardField wizardField = wizardInfo.getFields().get(field);
            if (wizardField.getAdded() && wizardField.getImportedName().toLowerCase(Locale.ROOT).contains(" where ")) {
                String jsonRule = wizardField.getImportedName();
                String errorFound = "Json rule should be of the form `<jsonpath>` where `peertag` = \"<jsonvalue>\" (e.g)  'data|value' where 'name'=\"contact_name\": " + jsonRule;
                String fieldName = getField(jsonRule, '`');
                if (fieldName == null) {
                    error = errorFound;
                    break;
                }
                int divPos = fieldName.lastIndexOf(String.valueOf(ImportService.JSONFIELDDIVIDER));
                if (divPos < 0) {
                    error = errorFound;
                    break;
                }
                jsonRule = jsonRule.substring(fieldName.length() + 2).trim();
                if (!jsonRule.toLowerCase(Locale.ROOT).startsWith("where ")) {
                    error = errorFound;
                    break;

                }
                jsonRule = jsonRule.substring(6).trim();
                String jsonField = getField(jsonRule, '`');
                if (jsonField == null) {
                    error = errorFound;
                    break;
                }
                jsonRule = jsonRule.substring(jsonField.length() + 2).trim();
                if (jsonRule.charAt(0) != '=') {
                    error = errorFound;
                    break;
                }
                jsonRule = jsonRule.substring(1).trim();
                String jsonValue = getField(jsonRule, '"');
                if (jsonValue == null) {
                    error = errorFound;
                    break;
                }
                String valueToTest = null;
                if (field.equals(testField)) {
                    valueToTest = testValue;
                }
                jsoNtests.add(new ImportWizard.JSONtest(field, fieldName, jsonField, jsonValue, valueToTest));
            }
        }

        return error;
    }
    public static int nthLastIndexOf(int nth, String ch, String string) {
        if (nth <= 0) return string.length();
        if (string.lastIndexOf(ch) == -1) return -1;//start of string
        return nthLastIndexOf(--nth, ch, string.substring(0, string.lastIndexOf(ch)));

    }


    public static boolean isWizardSetup(LoggedInUser loggedInUser, UploadedFile uploadedFile) {
        if (uploadedFile.getFileName().toLowerCase(Locale.ROOT).contains("azquo setup.")) {
            return true;
        }
        return false;
    }



    public static void setDistinctCount(WizardField wizardField) {
        Set<String> distinct = new HashSet<>();
        for (String value : wizardField.getValuesFound()) {
            if (value.trim().length() > 0 && !value.equalsIgnoreCase("null")) {
                distinct.add(value);
            }
        }
        wizardField.setDistinctCount(distinct.size());
    }

    public static void adjustValuesFound(WizardField wizardField, List<String> values) {

        if (DATELANG.equals(wizardField.getType()) || USDATELANG.equals(wizardField.getType())) {
            wizardField.setValuesFound(adjustDates(values, wizardField.getType()));
        } else {
            if ("time".equals(wizardField.getType())) {
                wizardField.setValuesFound(adjustTImes(values));
            } else {
                wizardField.setValuesFound(values);
            }
        }
        setDistinctCount(wizardField);
    }

    public static List<String> adjustDates(List<String> values, String dateType) {
        List<String> toReturn = new ArrayList<>();
        if (values==null){
            return null;
        }
        for (String value : values) {
            if (value.length() > 10) {
                value = value.substring(0, 10);
            }
            if (value.length()>0) {
                try {
                    if (dateType.equals(DATELANG)) {
                        DateUtils.isADate(value);
                    } else {
                        DateUtils.isUSDate(value);
                    }
                } catch (Exception e) {
                    value = "invalid date: " + value;
                }
            }
            toReturn.add(value);
        }
        return toReturn;
    }


    public static List<String> adjustTImes(List<String> values) {
        if (values==null) {
            return null;
        }
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


    public static String pad(int val, int len) {
        String toReturn = val + "";
        if (toReturn.length() < len) {
            return "000000".substring(0, len - toReturn.length()) + toReturn;
        }
        return toReturn;


    }


    public static String humaniseName(String name) {
        name = name.replace("_", " ").replace(ImportService.JSONFIELDDIVIDER + "", " ");
        name = name.substring(0, 1).toUpperCase() + name.substring(1);
        return name;

    }

    public static boolean traverseJSON(Map<String, List<String>> fieldsFound, String jsonPath, JSONObject jsonNext, int maxArraySize, String testItem, List<ImportWizard.JSONtest> jsonTests) throws Exception {
        boolean found = false;
        String[] jsonNames = JSONObject.getNames(jsonNext);
        boolean tested = false;
        for (ImportWizard.JSONtest jsoNtest : jsonTests) {
            if (jsoNtest.source.equals(jsonPath)) {
                try {
                    tested = true;
                    String jsonOtherValue = jsonNext.getString(jsoNtest.jsonPartner);
                    if (jsonOtherValue.equals(jsoNtest.jsonValue)) {
                        String jsonValue = jsonNext.getString(jsoNtest.jsonField);
                        if (jsoNtest.testValue == null || jsoNtest.testValue.equals(jsonValue)) {
                            try {
                                fieldsFound.get(jsoNtest.target).add(jsonValue);
                            } catch (Exception e) {
                                fieldsFound.put(jsoNtest.target, new ArrayList<>());
                                fieldsFound.get(jsoNtest.target).add(jsonValue);
                            }
                            if (jsoNtest.testValue != null) {
                                found = true;
                            }
                        }
                    }
                } catch (Exception e) {
                    //should we return an error?
                }
            }
        }
        if (tested) return found;
        for (String jsonName : jsonNames) {
            String newPath = jsonPath + ImportService.JSONFIELDDIVIDER + jsonName;
            if (testItem.length() > 0 && fieldsFound.get(newPath.substring(1)) != null) {
                fieldsFound.get(newPath.substring(1)).clear();
            }
            if (testItem.length() == 0 || testItem.startsWith(jsonPath)) {
                try {
                    JSONObject jsonObject = jsonNext.getJSONObject(jsonName);
                    if (traverseJSON(fieldsFound, newPath, jsonObject, maxArraySize, testItem, jsonTests)) {
                        found = true;
                    }
                    ;
                } catch (Exception e) {
                    try {

                        JSONArray jsonArray = jsonNext.getJSONArray(jsonName);
                        int count = 0;
                        for (Object jsonObject1 : jsonArray) {
                            if (found) break;
                            if (traverseJSON(fieldsFound, newPath, (JSONObject) jsonObject1, maxArraySize, testItem, jsonTests)) {
                                found = true;
                            }
                            ;
                            if (count++ == maxArraySize) {
                                break;
                            }

                        }
                    } catch (Exception e2) {

                        String value = jsonNext.get(jsonName).toString().trim();
                        if (value.length() > 0 && (testItem.equals(newPath + ImportService.JSONFIELDDIVIDER + value) || testItem.equals(newPath + ImportService.JSONFIELDDIVIDER + "*"))) {

                            found = true;
                        }
                        if (value.length() > 0) {
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

    public static String getField(String from, char quote) {
        if (from.length() < 2 || from.charAt(0) != quote || from.indexOf(String.valueOf(quote), 1) < 0) {
            return null;
        }
        return from.substring(1, from.indexOf(String.valueOf(quote), 1));

    }

    public static String insertSpaces(String field) {
        int i = 1;
        if (field.contains("_")){
            return field.replace("_"," ");
        }
        boolean lastCap = true;
        while (i < field.length()) {
            char c = field.charAt(i);
            if (c == ' ') {
                return field;
            }
            if (c >= 'A' && c <= 'Z') {
                if (!lastCap) {

                    field = field.substring(0, i) + " " + field.substring(i);

                    i++;
                }
                lastCap = true;
            } else {
                lastCap = false;
            }
            i++;
        }
        return field;

    }

    public static String lookupList(String field, Map<String,String>lookups){
        StringBuffer toReturn = new StringBuffer();
        toReturn.append(field);
        for (String lookup:lookups.keySet()){
            if (!lookup.equals(field) && lookups.get(lookup).equals(field)){
                toReturn.append("||" + lookup);
            }
        }
        return toReturn.toString();
    }

    public static String standardise(String value) {
        //not sure how the system read the cr as \\n
        return normalise(value).toLowerCase(Locale.ROOT).replace(" ", "");
    }


    public static String normalise(String value) {
        //not sure how the system read the cr as \\n
        return value.replace("\\\\n", " ").replace("\n", " ").replace("  ", " ").replace("_", " ");
    }

    public static String headingFrom(String value, Map<String, String> lookup) {
        value = standardise(value);
        String map = lookup.get(value);
        if (map != null) {
            return map;
        }
        return value;
    }


    public static boolean areNumbers(List<String> values) {
        boolean found = false;
        for (String value : values) {
            if (value.length() > 0 && !value.equalsIgnoreCase("null")) {
                found = true;
                if (value.charAt(0) == '0' && value.indexOf(".") < 0 && value.length()>1) {
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

    public static String getXLTerm(WizardInfo wizardInfo, String field, int i) {
        String sourceVal = wizardInfo.getFields().get(field).getValuesFound().get(i);
        if (NumberUtils.isNumber(sourceVal)) {
            return sourceVal;
        }
        return "\"" + sourceVal + "\"";

    }


    public static boolean isAttribute(WizardInfo wizardInfo, String field, String field2) {
        List<String> maybeKey = wizardInfo.getFields().get(field).getValuesFound();
        List<String> maybeAttribute = wizardInfo.getFields().get(field2).getValuesFound();
        Map<String, Set<String>> mapping = new HashMap<>();
        for (int i = 0; i < maybeAttribute.size(); i++) {
            Set<String> map = mapping.get(maybeAttribute.get(i));
            if (map == null) {
                mapping.put(maybeAttribute.get(i), new HashSet<>());
                map = mapping.get(maybeAttribute.get(i));
            }
            map.add(maybeKey.get(i));
        }
        for (String parent : mapping.keySet()) {
            if (mapping.get(parent).size() > 1) {
                return false;
            }
        }
        return true;


    }
    public static boolean isIdField(WizardInfo wizardInfo, String fieldName) {
        if (ImportUtils.nameField(fieldName) == null)
            return false;
        if (wizardInfo.getFields().get(fieldName).getAnchor() != null || wizardInfo.getFields().get(fieldName).getParent() != null) {
            return false;
        }
        return true;
    }


    public static boolean isParentChild(WizardInfo wizardInfo, String field, String field2) {
        List<String> maybeParent = wizardInfo.getFields().get(field).getValuesFound();
        List<String> maybeChild = wizardInfo.getFields().get(field2).getValuesFound();
        String childType = wizardInfo.getFields().get(field2).getType();
        if (ImportUtils.DATELANG.equals(childType) || ImportUtils.USDATELANG.equals(childType)) {
            return false;
        }
        Map<String, Set<String>> mapping = new HashMap<>();
        if (maybeParent.size() < 4) {
            return false;
        }
        for (int i = 0; i < maybeParent.size(); i++) {
            if (maybeParent.get(i).length() > 0) {
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
        for (String parent : mapping.keySet()) {
            totalCount += mapping.get(parent).size();
        }
        //checking that each of the potential children occurs in only one parent set
        if (totalCount < wizardInfo.getFields().get(field2).getDistinctCount() * 1.01) {
            //allowing for some mis-categorisation (1%)
            return true;
        }
        return false;
    }


    public static void calcXL(WizardInfo wizardInfo, int lineCount) throws Exception {

        if (lineCount==0){
            lineCount = wizardInfo.getLineCount();
        }
        for (String maybeXL : wizardInfo.getFields().keySet()) {
            WizardField xlField = wizardInfo.getFields().get(maybeXL);
            if (xlField.getSpecialInstructions() != null) {
                String specialInstructions = xlField.getSpecialInstructions();
                String[] specials = specialInstructions.split(";");
                for (String special : specials) {
                    if (special.startsWith("=")) {

                        Map<String, String> fieldList = new HashMap<>();
                        for (String field : wizardInfo.getFields().keySet()) {
                            WizardField wizardField = wizardInfo.getFields().get(field);
                            if (special.toLowerCase(Locale.ROOT).contains(StringLiterals.QUOTE + wizardField.getName().toLowerCase(Locale.ROOT) + StringLiterals.QUOTE)) {
                                fieldList.put(StringLiterals.QUOTE + wizardField.getName() + StringLiterals.QUOTE, field);
                            }
                        }
                        xlField.setValuesFound(new ArrayList<>());
                        String excelFormula = null;
                        for (int i = 0; i < lineCount; i++) {
                            String compositionPattern = special.substring(1).trim();
                            StringBuilder modifiedPattern = new StringBuilder();
                            Pattern p = Pattern.compile("" + StringLiterals.QUOTE + "[^" + StringLiterals.QUOTE + "]*" + StringLiterals.QUOTE); //`name`.`attribute`
                            Matcher matcher = p.matcher(compositionPattern);
                            int lastEnd = 0;
                            while (matcher.find()) {
                                if (modifiedPattern.length() == 0) {
                                    modifiedPattern.append(compositionPattern, 0, matcher.start());
                                } else {
                                    modifiedPattern.append(compositionPattern, lastEnd, matcher.start());
                                }
                                lastEnd = matcher.end();
                                String fieldName = compositionPattern.substring(matcher.start(), matcher.end()).replace(StringLiterals.QUOTE+"","");
                                String field = ImportUtils.findFieldFromName(wizardInfo,fieldName);
                                if (field == null) {
                                    throw new Exception("cannot find the field " + fieldName);
                                }
                                WizardField wizardField = wizardInfo.getFields().get(field);
                                String valFound = "\"\"";
                                if (wizardField.getValuesFound()!=null && i <  wizardField.getValuesFound().size()) {
                                    valFound = wizardField.getValuesFound().get(i);
                                    if (!NumberUtils.isNumber(valFound)) {
                                        valFound = "\"" + valFound + "\"";
                                    }
                                }
                                modifiedPattern.append(valFound);
                            }
                            modifiedPattern.append(compositionPattern.substring(lastEnd));

                            excelFormula = modifiedPattern.toString();
                            excelCell.setCellFormula(excelFormula);
                            formulaEvaluator.clearAllCachedResultValues();
                            String result = formulaEvaluator.evaluate(excelCell).formatAsString();
                            if (result.startsWith("\"") && result.endsWith("\"")) {
                                result = result.substring(1, result.length() - 1).trim();
                            }
                            // for Excel date is a number - on the way out ImportUtils.standardise to our typically used date format
                            if (ImportUtils.DATELANG.equals(xlField.getType()) || ImportUtils.USDATELANG.equals(xlField.getType())) {
                                try {
                                    result = DateUtils.toDate(result);
                                } catch (Exception e) {
                                    throw new Exception("Cannot read : " + compositionPattern + " as date. Try surrounding with DATEVALUE()?");
                                }
                            }
                            xlField.getValuesFound().add(result);
                        }
                        ImportUtils.setDistinctCount(xlField);
                    }
                }
            }

        }
    }

    public static String getTemplateName(LoggedInUser loggedInUser){
        String fileName = loggedInUser.getWizardInfo().getImportFile().getFileName();
        int blankPos = fileName.indexOf(" ");
        if (blankPos < 0){
            blankPos = fileName.indexOf(".");
        }
        return fileName.substring(0, blankPos);

    }

    public static int findColNo(Map<String,WizardField> wizardFields, String toFind){
        int count = 0;
        for (String field:wizardFields.keySet()){
            if (field.equals(toFind)){
                return count;
            }
            count++;
        }
        return -1;
    }

    public static String cellAsString(int row, int col){
        String colString = "";
        if (col< 26){
            colString+=(char)(col+65);
        }else {
            colString += (char) (col / 26 + 64);
            colString += (char) (col % 26 + 65);
        }
        return "$"+colString + "$" + (row + 1);
    }

    public static void setKeikaiCell(Sheet sheet, int row, int col, String value){
        SCell cell = sheet.getInternalSheet().getCell(row, col);
        BookUtils.setValue(cell, value);

    }

    public static void poiSetSingleCellRange(Workbook book, String rangeName, String value){
        org.apache.poi.ss.usermodel.Name region = BookUtils.getName(book, rangeName);
        AreaReference areaRef = new AreaReference(region.getRefersToFormula(), null);
        book.getSheet(region.getSheetName()).getRow(areaRef.getFirstCell().getRow()).getCell(areaRef.getFirstCell().getCol()).setCellValue(value);

    }



}
