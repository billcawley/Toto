package com.azquo.dataimport;

import org.json.JSONObject;

import java.util.*;

public class WizardInfo {
    String importFileName;
    JSONObject importFileData;
    Map<String, WizardField> fields;
    Map<String,WizardField> matchFields;
    String lastDataField;
    int lineCount;
    int maxStageReached;
    List<String> suggestionActions;
    Map<String,String>lookups;

    public WizardInfo(String importFileName, JSONObject importFileData){
        this.importFileName = importFileName;
        this.importFileData = importFileData;
        this.fields = new LinkedHashMap<>();
        this.matchFields = null;
        this.lastDataField = null;
        this.lineCount = 0;
        this.maxStageReached = 0;
        this.suggestionActions = new ArrayList<>();
        this.lookups = new HashMap<>();
     }

    public String getImportFileName() {
        return importFileName;
    }

    public JSONObject getImportFileData(){return importFileData; }

    public Map<String,WizardField>getFields(){return fields; }

    public void setFields(Map<String,WizardField>fields){this.fields = fields; }

    public Map<String,WizardField>getMatchFields(){return matchFields; }

    public void setMatchFields(Map<String,WizardField>matchFields){this.matchFields = matchFields; }

    public String getLastDataField(){return lastDataField; }

    public void setLastDataField(String lastDataField){this.lastDataField = lastDataField; }

    public int getLineCount(){return lineCount; }

    public void setLineCount(int lineCount){this.lineCount = lineCount; }

    public int getMaxStageReached(){return maxStageReached; }

    public void setMaxStageReached(int maxStageReached){this.maxStageReached = maxStageReached; }

    public List<String> getSuggestionActions(){return suggestionActions; }

    public void setSuggestionActions(List<String>suggestionActions){this.suggestionActions = suggestionActions; }

    public Map<String,String>getLookups(){return lookups; }

    public void setLookups(Map<String,String>lookups){this.lookups = lookups; }




}
