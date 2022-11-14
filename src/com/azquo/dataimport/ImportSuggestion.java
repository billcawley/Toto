package com.azquo.dataimport;

import com.azquo.DateUtils;
import org.apache.commons.lang.math.NumberUtils;

import java.util.*;

public class ImportSuggestion 
{

    public static String makeSuggestion(WizardInfo wizardInfo, int stage) {
        List<String> suggestions = new ArrayList<>();
        wizardInfo.setLastDataField(null);
        String suggestionReason = "";
        String keyField = null;
        String keyFieldRoot = null;
        String keyFieldName = null;
        List <String>peers = new ArrayList();
        int keyFieldCount = 0;
        boolean usDate = false;
        for (String field : wizardInfo.getFields().keySet()) {
            WizardField wizardField = wizardInfo.getFields().get(field);
            if (ImportWizard.KEYFIELDID.equals(wizardField.getType())) {
                keyField = field;
                keyFieldName = wizardField.getName();
                peers.add(field);
                if(wizardField.getValuesFound() !=null){
                    keyFieldCount = wizardField.getValuesFound().size();
                }else{
                    keyFieldCount = wizardInfo.getLineCount();
                }
            }
        }

        try {
            boolean hasSuggestion = false;
            switch (stage) {
                case ImportWizard.NAMESTAGE:

                    //check names for short names
                    Set<String> shortNames = new HashSet<>();
                    for (String field : wizardInfo.getFields().keySet()) {
                        WizardField wizardField = wizardInfo.getFields().get(field);
                        if (!wizardField.getImportedName().equals(wizardField.getName())) {
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
                                    Map<String, WizardField> list = ImportUtils.getDataValuesFromFile(wizardInfo, field, shortName);
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
                                        Map<String, WizardField> list = ImportUtils.getDataValuesFromFile(wizardInfo, field, shortName);
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

                case ImportWizard.TYPESTAGE:
                    //dates times, key fields
                    keyField = wizardInfo.getFields().keySet().iterator().next();

                    if (ImportUtils.isIdField(wizardInfo,keyField) && wizardInfo.getFields().get(keyField).getDistinctCount()==wizardInfo.getLineCount()) {
                        keyFieldRoot = keyField.toLowerCase(Locale.ROOT);
                        WizardField wizardField = wizardInfo.getFields().get(keyField);
                        wizardField.setType(ImportWizard.KEYFIELDID);
                        hasSuggestion = true;

                    } else {
                        keyField = null;
                    }
                    if (keyField != null) {
                        for (String field : wizardInfo.getFields().keySet()) {
                            if (field.toLowerCase(Locale.ROOT).endsWith("name") && field.toLowerCase(Locale.ROOT).startsWith(keyFieldRoot)) {
                                wizardInfo.getFields().get(field).setType(ImportWizard.KEYFIELDNAME);
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
                        boolean found = false;
                        for (int i = 0; i < valCOunt; i++) {
                            String val = wizardField.getValuesFound().get(i);
                            if (val.length() > 10) {
                                val = val.substring(0, 10);
                            }
                            if (val.length() > 0) {
                                found = true;
                                java.time.LocalDate date = DateUtils.isADate(val);
                                if (date == null) {
                                    date = DateUtils.isUSDate(val);
                                    if (date == null) {
                                        found = false;
                                        break;
                                    } else {
                                        usDate = true;
                                    }
                                }
                            }
                        }
                        if (found && wizardField.getType() == null) {
                            if (usDate) {
                                wizardField.setType(ImportUtils.USDATELANG);
                            } else {
                                wizardField.setType(ImportUtils.DATELANG);
                            }
                            hasSuggestion = true;
                        }
                        found = false;
                        for (int i = 0; i < valCOunt; i++) {
                            try {
                                String val = wizardField.getValuesFound().get(i);
                                if (val.length() > 0) {
                                    int j = Integer.parseInt(wizardField.getValuesFound().get(i));
                                    if (j>0){
                                        found = true;
                                    }
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
                        if (wizardField.getType()==null && wizardField.getImportedName().toLowerCase(Locale.ROOT).endsWith("date")){
                            wizardField.setType(ImportUtils.DATELANG);
                            hasSuggestion = true;
                        }
                    }
                    if (hasSuggestion) {
                        return "we gave filled in some suggestions";
                    }
                case ImportWizard.DATASTAGE:
                    String parent = null;
                    if (wizardInfo.getImportFileData() != null) {
                        int maxCount = 0;
                        for (String field : wizardInfo.getFields().keySet()) {
                            WizardField wizardField = wizardInfo.getFields().get(field);

                            if (wizardField.getParent() != null) {
                                break;//only suggest if no data fields are already defined
                            }
                            if (ImportWizard.KEYFIELDNAME.equals(wizardField.getType())) {
                                parent = wizardField.getName() + " data";
                            }
                            if (wizardField.getValuesFound().size() > maxCount) {
                                maxCount = wizardField.getValuesFound().size();

                            }
                        }
                        List<String> possibleData = new ArrayList<>();
                        for (String field : wizardInfo.getFields().keySet()) {
                            WizardField wizardField = wizardInfo.getFields().get(field);
                            if (wizardField.getValuesFound().size() * 2 >= maxCount && !Arrays.asList(ImportUtils.TYPEOPTIONS).contains(wizardField.getType())) {//the number of different instances is more than half the max number found
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
                        parent = wizardInfo.getImportSchedule().getTemplateName() + " Values";
                        //suggestion.append("possible data fields are: ");
                        for (String field : wizardInfo.getFields().keySet()) {
                            WizardField wizardField = wizardInfo.getFields().get(field);
                            if (!ImportUtils.isIdField(wizardInfo, field) && !ImportUtils.isKeyField(wizardField) && ImportUtils.areNumbers(wizardField.getValuesFound())) {
                                suggestions.add(wizardInfo.getFields().get(field).getName());
                                wizardField.setParent(parent);
                                if (peers.size() > 0) {
                                     wizardField.setPeers(peers);
                                }
                                suggestionReason = "These are the fields with all numeric values, which are not IDs.";
                            }
                         }
                        if (suggestionReason.length()>0 && peers.size()==0){
                            for (String field1:wizardInfo.getFields().keySet()){
                                if (peers.size() > 0) {
                                    break;
                                }
                               WizardField wizardField1 = wizardInfo.getFields().get(field1);
                                boolean testing = false;
                                for (String field2:wizardInfo.getFields().keySet()){
                                    if (field2.equals(field1)){
                                        testing = true;
                                    }else{
                                        if (testing && wizardField1.getParent()==null){
                                            WizardField wizardField2 = wizardInfo.getFields().get(field2);
                                            if (wizardField2.getParent()== null && wizardField1.getDistinctCount() * wizardField2.getDistinctCount() > wizardInfo.lineCount){
                                                //test for distinct combos
                                                Set<String> combos = new HashSet<>();
                                                try {
                                                    boolean duplicate = false;
                                                    for (int i = 0; i < wizardInfo.getLineCount(); i++) {
                                                          String val = wizardField1.getValuesFound().get(i) + " " + wizardField2.getValuesFound().get(i);
                                                          if (combos.contains(val)){
                                                              duplicate = true;
                                                              break;
                                                          }
                                                          combos.add(val);
                                                    }
                                                    if (!duplicate) {
                                                        peers.add(field1);
                                                        peers.add(field2);
                                                        break;
                                                    }
                                                }catch(Exception e){
                                                }
                                            }

                                        }
                                    }
                                }
                            }
                            if (peers.size()>0){
                                for (String field:wizardInfo.getFields().keySet()){
                                    WizardField wizardField = wizardInfo.getFields().get(field);
                                    if (wizardField.getParent()!=null){
                                        wizardField.setPeers(peers);
                                    }
                                }
                            }
                        }
                        if (suggestionReason.length() == 0) {
                            if (keyFieldName != null) {
                                String countName = keyField + " count";
                                suggestionReason = "No data fields found.  Suggest adding a data field: " + countName;
                                WizardField wizardField = new WizardField(ImportUtils.standardise(countName), countName, true);
                                wizardField.setSpecialInstructions("=1");
                                try {
                                    ImportUtils.calcXL(wizardInfo, 100);
                                }catch(Exception e){

                                }
                                peers.add(keyField);
                                wizardField.setParent(parent);
                                ;
                                wizardField.setPeers(peers);
                                wizardInfo.getFields().put("1", wizardField);
                                try {
                                    ImportUtils.calcXL(wizardInfo, 100);
                                } catch (Exception e) {

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

                case ImportWizard.PARENTSTAGE:
                    Set<String> attributeList = new HashSet<>();
                    for (String field : wizardInfo.getFields().keySet()) {
                        if (ImportUtils.isIdField(wizardInfo, field)) {
                            WizardField nameField = wizardInfo.getFields().get(ImportUtils.nameField(field));
                            if (nameField != null) {
                                nameField.setAnchor(field);
                                suggestionReason += "<li>" + nameField.getName() + " is an attribute of " + wizardInfo.getFields().get(field).getName() + " (based on field names)</li>";
                                attributeList.add(field);
                            }
                        }
                    }
                    if (keyField != null) {
                        for (String field : wizardInfo.getFields().keySet()) {
                            WizardField wizardField = wizardInfo.getFields().get(field);
                            if (wizardField.getParent()==null && !attributeList.contains(field) && !ImportUtils.isKeyField(wizardField) && wizardField.getDistinctCount() == keyFieldCount) {
                                wizardField.setAnchor(keyField);
                                suggestionReason += "<li>" + wizardField.getName() + " is an attribute of " + keyFieldName + " (all values re unique) </li>";
                                attributeList.add(field);
                            }
                            if (wizardField.getParent()==null && wizardField.getDistinctCount() == 0) {
                                wizardField.setAnchor(keyField);
                                suggestionReason += "<li>" + wizardField.getName() + " is an attribute of " + keyFieldName + " (no data!) </li>";
                                attributeList.add(field);
                            }
                        }

                    }
                    if (wizardInfo.getImportFileData() == null) {
                        Map<String, WizardField> wizardFields = wizardInfo.getFields();
                        List<String> fields = new ArrayList<>();
                        for (String field : wizardFields.keySet()) {
                            fields.add(field);
                        }
                        fields.sort((o1, o2) -> {
                            return wizardFields.get(o2).getDistinctCount() - wizardFields.get(o1).getDistinctCount();
                        });
                        for (String field : fields) {
                            WizardField wizardField = wizardInfo.getFields().get(field);
                            int potentialKeyCount = wizardField.getDistinctCount();
                            if (wizardField.getType() == null && !attributeList.contains(field) && potentialKeyCount > 1) {

                                for (String field2 : fields) {
                                    WizardField wizardField2 = wizardInfo.getFields().get(field2);
                                    if (!attributeList.contains(field2) && !ImportUtils.isKeyField(wizardField2) && !ImportUtils.isIdField(wizardInfo, field2) && !field2.equals(field) && wizardField2.getDistinctCount() <= potentialKeyCount && wizardField2.getDistinctCount() > potentialKeyCount * 0.5) {

                                        if (ImportUtils.isAttribute(wizardInfo, field, field2)) {
                                            wizardField2.setAnchor(field);
                                            suggestionReason += "<li>" + wizardField2.getName() + " is an attribute of " + wizardField.getName() + " (there is a 1:1 value relationship) </li>";
                                            attributeList.add(field2);
                                        }
                                    }
                                }
                            }
                        }
                    }
                    List<String> attributeParentList = new ArrayList<>();
                    if (wizardInfo.getImportFileData() == null) {
                        Map<String, WizardField> wizardFields = wizardInfo.getFields();
                        List<String> fields = new ArrayList<>();
                        for (String field : wizardFields.keySet()) {
                            WizardField wizardField = wizardInfo.getFields().get(field);
                            if (wizardField.getParent() == null && !ImportWizard.KEYFIELDNAME.equals(wizardField.getType()) && wizardField.getSelect()) {
                                fields.add(field);
                            }
                        }
                        fields.sort((o1, o2) -> {
                            return wizardFields.get(o2).getDistinctCount() - wizardFields.get(o1).getDistinctCount();
                        });
                        //adding to 'attributeList' now only to remove from potential parent list.
                        for (String field : fields) {
                            WizardField wizardField = wizardFields.get(field);
                            int potentialKeyCount = wizardFields.get(field).getDistinctCount();
                            if (wizardField.getParent() != null || ImportUtils.isDate(wizardField) || (potentialKeyCount > wizardInfo.lineCount * 0.94 && potentialKeyCount < wizardInfo.lineCount)) {
                                attributeList.add(field);
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
                                    if (wizardFields.get(field2).getDistinctCount() >= potentialParentCount / 0.94) {
                                        if (ImportUtils.isParentChild(wizardInfo, field, field2)) {

                                            wizardFields.get(field).setChild(field2);
                                            suggestionReason += "<li>" + wizardFields.get(field).getName() + " is a parent of " + wizardFields.get(field2).getName() + " (there is a one:many relationship)</li>";
                                            break;
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (keyField != null) {
                        for (String field : wizardInfo.getFields().keySet()) {
                            WizardField wizardField = wizardInfo.getFields().get(field);
                            if (!ImportUtils.isKeyField(wizardField) && wizardField.getParent() == null && wizardField.getAnchor() == null && wizardField.getChild() == null) {
                                wizardField.setAnchor(keyField);

                            }
                        }
                        suggestionReason += "</br><p>All fields that are not parents have been defaulted to attributes of the key field</p>";

                    }
                    if (suggestionReason.length() > 0) {
                        return "<p>We&#x27;ve automatically identified <strong>potential</strong> relationships. Here can edit parent/children and attribute relationships</p><ul> " + suggestionReason + "</ul>";
                    }
                    return "";
            }


        } catch (Exception e) {
            return "Error:" + e.getMessage();
        }
        return "";
    }



}
