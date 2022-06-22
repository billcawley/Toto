package com.azquo.dataimport;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class WizardInfo {
    String importFileName;
    String importFileData;
    Map<String, WizardField> fields;
    String lastDataField;
    int lineCount;
    boolean hasSuggestions;
    List<String> suggestionActions;

    public WizardInfo(String importFileName, String importFileData){
        this.importFileName = importFileName;
        this.importFileData = importFileData;
        this.fields = new LinkedHashMap<>();
        this.lastDataField = null;
        this.lineCount = 0;
        this.hasSuggestions = true;
        this.suggestionActions = new ArrayList<>();
     }

    public String getImportFileName() {
        return importFileName;
    }

    public String getImportFileData(){return importFileData; }

    public Map<String,WizardField>getFields(){return fields; }

    public void setFields(Map<String,WizardField>fields){this.fields = fields; }

    public String getLastDataField(){return lastDataField; }

    public void setLastDataField(String lastDataField){this.lastDataField = lastDataField; }

    public int getLineCount(){return lineCount; }

    public void setLineCount(int lineCount){this.lineCount = lineCount; }

    public boolean getHasSuggestions(){return hasSuggestions; }

    public void setHasSuggestions(boolean hasSuggestions){this.hasSuggestions = hasSuggestions; }

    public List<String> getSuggestionActions(){return suggestionActions; }

    public void setSuggestionActions(List<String>suggestionActions){this.suggestionActions = suggestionActions; }




}
