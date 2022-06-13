package com.azquo.dataimport;

import java.util.List;

public class WizardField {
    private String name;
    private List<String> valuesFound;
    private String interpretation;
    private boolean ignore;
    private String type;
    private String parent;
    private List<String> peers;
    private String child;
    private String anchor;

    public WizardField(String name, List<String> valuesFound, String interpretation){
        this.name = name;
        this.valuesFound = valuesFound;
        this.interpretation = interpretation;
        this.ignore = false;
        this.type = null;
        this.parent = null;
        this.peers = null;
        this.child = null;
        this.anchor = null;
    }

    public String getName() {return name; }
    public void setName(String name){this.name = name; }

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
}
