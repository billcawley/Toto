package com.azquo.dataimport;

import java.util.ArrayList;
import java.util.List;

public class
WizardField {
    private String importedName;
    private String name;
    private List<String> valuesFound;
    private String valueFound;
    private int distinctCount;
    private String interpretation;
    private boolean select;
    private String type;
    private String parent;
    private List<String> peers;
    private String child;
    private String specialInstructions;
    private String anchor;
    private boolean added;
    //only used when reading the template
    private List<String>templateClauses;
    //only used when setting up unmatched imports
    private String matchedFieldName;

    public WizardField(String importedName, String name, boolean added){
        this.importedName = importedName;
        this.name = name;
        this.valuesFound = null;
        this.distinctCount = 0;
        this.valueFound = null;
        this.interpretation = null;
        this.select = true;
        this.type = null;
        this.parent = null;
        this.peers = null;
        this.child = null;
        this.specialInstructions = "";
        this.anchor = null;
        this.added = added;
        this.templateClauses = new ArrayList<>();
        this.matchedFieldName = null;
    }


    public String getImportedName(){return importedName; }

    public void setImportedName(String importedName){this.importedName = importedName; }

    public String getName() {return name; }
    public void setName(String name){this.name = name; }

    public List<String> getValuesFound(){return valuesFound; }

    public void setValuesFound(List<String>valuesFound){this.valuesFound = valuesFound; }

    public int getDistinctCount(){ return distinctCount; }

    public void setDistinctCount(int distinctCount){this.distinctCount = distinctCount; }

    public String getValueFound(){return valueFound; }

    public void setValueFound(String valueFound){this.valueFound = valueFound; }


    public java.lang.String getInterpretation() {
        return interpretation;
    }

    public void setInterpretation(String interpretation){this.interpretation = interpretation; }

    public boolean getSelect(){return select; }

    public void setSelect(boolean select){this.select = select; }

    public String getType(){return  type; }

    public void setType(String type){this.type = type; }

    public String getParent(){return  parent; }

    public void setParent(String parent){this.parent = parent; }

    public List<String> getPeers() {return peers;  }

    public void setPeers(List<String>peers){this.peers = peers; }

    public String getChild(){return child; }

    public void setChild(String child){this.child = child; }

    public String getSpecialInstructions(){return specialInstructions; }

    public void setSpecialInstructions(String specialInstructions){this.specialInstructions = specialInstructions; }

    public String getAnchor(){return anchor; }

    public void setAnchor(String anchor){this.anchor = anchor; }

    public boolean getAdded(){return added; }

    public void setAdded(boolean added){this.added = added; }

    public List<String>getTemplateClauses(){return templateClauses; }

    public void setTemplateClauses(List<String>templateClauses){this.templateClauses = templateClauses; }

    public String getMatchedFieldName(){return matchedFieldName; }

    public void setMatchedFieldName(String matchedFieldName){this.matchedFieldName = matchedFieldName; }
}
