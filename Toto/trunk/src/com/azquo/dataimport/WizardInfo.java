package com.azquo.dataimport;

import com.azquo.spreadsheet.transport.UploadedFile;
import io.keikai.api.model.Book;
import org.json.JSONObject;

import java.util.*;

public class WizardInfo {
    UploadedFile importFile;
    JSONObject importFileData;
    Map<String, WizardField> fields;
    Map<String,Map<String,WizardField>> extraTemplateFields;
    Map<String,WizardField> matchFields;
    String lastDataField;
    int lineCount;
    int maxStageReached;
    String templateName;
    Map<String,String> templateParameters;
    Map<String,String>lookups;
    Book preprocessor;

    public WizardInfo(UploadedFile importFile, JSONObject importFileData){
        this.importFile = importFile;
        this.importFileData = importFileData;
        this.fields = new LinkedHashMap<>();
        this.extraTemplateFields = new HashMap<>();
        this.matchFields = null;
        this.lastDataField = null;
        this.lineCount = 0;
        this.maxStageReached = 0;
        this.templateName = null;
        this.templateParameters = new HashMap<>();
        this.lookups = new HashMap<>();
        this.preprocessor = null;

     }

    public UploadedFile getImportFile() {
        return importFile;
    }

    public JSONObject getImportFileData(){return importFileData; }

    public void setImportFileData(JSONObject importFileData){this.importFileData = importFileData; }

    public Map<String,WizardField>getFields(){return fields; }

    public void setFields(Map<String,WizardField>fields){this.fields = fields; }

    public Map<String,Map<String,WizardField>>getExtraTemplateFields(){return extraTemplateFields; }

    public void setExtraTemplateFields(Map<String,Map<String,WizardField>>extraTemplateFields){this.extraTemplateFields = extraTemplateFields; }

    public Map<String,WizardField>getMatchFields(){return matchFields; }

    public void setMatchFields(Map<String,WizardField>matchFields){this.matchFields = matchFields; }

    public String getLastDataField(){return lastDataField; }

    public void setLastDataField(String lastDataField){this.lastDataField = lastDataField; }

    public int getLineCount(){return lineCount; }

    public void setLineCount(int lineCount){this.lineCount = lineCount; }

    public int getMaxStageReached(){return maxStageReached; }

    public void setMaxStageReached(int maxStageReached){this.maxStageReached = maxStageReached; }

     public String getTemplateName(){return templateName; }

    public void setTemplateName(String templateName){this.templateName = templateName; }

    public Map<String,String> getTemplateParameters() {return templateParameters;   }

    public void setTemplateParameters(Map<String,String> templateParameters){this.templateParameters = templateParameters; }

    public Map<String,String> getLookups(){return lookups; }

    public void setLookups(Map<String,String> lookups){this.lookups = lookups;  }

    public Book getPreprocessor(){return preprocessor; }

    public void setPreprocessor(Book preprocessor){this.preprocessor = preprocessor; }


}
