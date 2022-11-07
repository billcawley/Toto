package com.azquo.dataimport;

import com.azquo.spreadsheet.transport.UploadedFile;
import io.keikai.api.model.Book;
import org.json.JSONObject;

import java.util.*;

public class WizardInfo {
    UploadedFile importFile;
    JSONObject importFileData;
    ImportSchedule importSchedule;
    Map<String, WizardField> fields;
    ImportTemplate importTemplate;
    Map<String,Map<String,WizardField>> extraTemplateFields;
    Map<String,WizardField> matchFields;
    String lastDataField;
    int lineCount;
    int maxStageReached;
    Map<String,String> templateParameters;
    Map<String,String>lookups;
    Book preprocessor;
    List<Integer>badLines;

    public WizardInfo(UploadedFile importFile, ImportSchedule importSchedule, JSONObject importFileData){
        this.importFile = importFile;
        this.importFileData = importFileData;
        this.importSchedule = importSchedule;
        this.importSchedule = null;
        this.fields = new LinkedHashMap<>();
        this.importTemplate = null;
        this.extraTemplateFields = new LinkedHashMap<>();
        this.matchFields = null;
        this.lastDataField = null;
        this.lineCount = 0;
        this.maxStageReached = 0;
        this.templateParameters = new HashMap<>();
        this.lookups = new HashMap<>();
        this.preprocessor = null;
        this.badLines = new ArrayList<>();


     }

    public UploadedFile getImportFile() {
        return importFile;
    }

    public JSONObject getImportFileData(){return importFileData; }

    public void setImportFileData(JSONObject importFileData){this.importFileData = importFileData; }

    public ImportSchedule getImportSchedule(){return importSchedule; }

    public void setImportSchedule(ImportSchedule importSchedule){this.importSchedule = importSchedule; }

    public Map<String,WizardField>getFields(){return fields; }

    public void setFields(Map<String,WizardField>fields){this.fields = fields; }

    public ImportTemplate getImportTemplate(){return importTemplate; }

    public void setImportTemplate(ImportTemplate importTemplate){this.importTemplate = importTemplate; }

    public Map<String,Map<String,WizardField>>getExtraTemplateFields(){return extraTemplateFields; }

    public Map<String,WizardField>getMatchFields(){return matchFields; }

    public void setMatchFields(Map<String,WizardField>matchFields){this.matchFields = matchFields; }

    public String getLastDataField(){return lastDataField; }

    public void setLastDataField(String lastDataField){this.lastDataField = lastDataField; }

    public int getLineCount(){return lineCount; }

    public void setLineCount(int lineCount){this.lineCount = lineCount; }

    public int getMaxStageReached(){return maxStageReached; }

    public void setMaxStageReached(int maxStageReached){this.maxStageReached = maxStageReached; }

     public Map<String,String> getTemplateParameters() {return templateParameters;   }

    public void setTemplateParameters(Map<String,String> templateParameters){this.templateParameters = templateParameters; }

    public Map<String,String> getLookups(){return lookups; }

    public void setLookups(Map<String,String> lookups){this.lookups = lookups;  }

    public Book getPreprocessor(){return preprocessor; }

    public void setPreprocessor(Book preprocessor){this.preprocessor = preprocessor; }

    public List<Integer>getBadLines(){return badLines; }

    public void setBadLines(List<Integer>badLines){this.badLines = badLines; }

  }
