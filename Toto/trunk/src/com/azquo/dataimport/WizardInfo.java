package com.azquo.dataimport;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class WizardInfo {
    String importFileName;
    String importFileData;
    Map<String, WizardField> fields;
    String lastDataField;

    public WizardInfo(String importFileName, String importFileData){
        this.importFileName = importFileName;
        this.importFileData = importFileData;
        this.fields = new LinkedHashMap<>();
        this.lastDataField = null;
     }

    public String getImportFileName() {
        return importFileName;
    }

    public String getImportFileData(){return importFileData; }

    public Map<String,WizardField>getFields(){return fields; }

    public void setFields(Map<String,WizardField>fields){this.fields = fields; }

    public String getLastDataField(){return lastDataField; }

    public void setLastDataField(String lastDataField){this.lastDataField = lastDataField; }


}
