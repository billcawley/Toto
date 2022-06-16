package com.azquo.dataimport;

import java.util.List;

public class WizardField {
    private String importedName;
    private String name;
    private List<String> valuesOnFile;
    private List<String> valuesFound;
    private String interpretation;
    private boolean ignore;
    private String type;
    private String parent;
    private List<String> peers;
    private String child;
    private String anchor;
    private boolean added;

    public WizardField(String importedName, String name, boolean added){
        this.importedName = importedName;
        this.name = name;
        this.valuesOnFile = null;
        this.valuesFound = null;
        this.interpretation = null;
        this.ignore = false;
        this.type = null;
        this.parent = null;
        this.peers = null;
        this.child = null;
        this.anchor = null;
        this.added = added;
    }


    public String getImportedName(){return importedName; }

    public void setImportedName(String importedName){this.importedName = importedName; }

    public String getName() {return name; }
    public void setName(String name){this.name = name; }

    public List<String> getValuesOnFile(){return valuesOnFile; }

    public void setValuesOnFile(List<String>valuesOnFile){this.valuesOnFile = valuesOnFile; }

    public List<String> getValuesFound(){return valuesFound; }

    public void setValuesFound(List<String>valuesFound){this.valuesFound = valuesFound; }

    public java.lang.String getInterpretation() {
        return interpretation;
    }

    public void setInterpretation(String interpretation){this.interpretation = interpretation; }

    public boolean getIgnore(){return ignore; }

    public void setIgnore(boolean ignore){this.ignore = ignore; }

    public String getType(){return  type; }

    public void setType(String type){this.type = type; }

    public String getParent(){return  parent; }

    public void setParent(String parent){this.parent = parent; }

    public List<String> getPeers() {return peers;  }

    public void setPeers(List<String>peers){this.peers = peers; }

    public String getChild(){return child; }

    public void setChild(String child){this.child = child; }

    public String getAnchor(){return anchor; }

    public void setAnchor(String anchor){this.anchor = anchor; }

    public boolean getAdded(){return added; }

    public void setAdded(boolean added){this.added = added; }
}
