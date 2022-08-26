/*
Copyright (C) 2022 Azquo Holdings Ltd.
        * <p>
        */


package com.azquo.dataimport;

import java.util.List;

public class ImportWizardField {
    String importName;
    String fieldName;
    List<String> valuesFound;
    String textEntry;
    List<String> listEntry;
    List<String>list2Entry;
    String checkEntry;
    String check2Entry;
    String example;

    ImportWizardField(String importName){
        this.importName = importName;
        this.fieldName = "";
        this.valuesFound = null;
        this.textEntry = null;
        this.listEntry = null;
        this.list2Entry = null;
        this.checkEntry = null;
        this.check2Entry = null;
        this.example = null;

    }

    String getImportName(){return importName; }

    void setFieldName(String fieldName){this.fieldName = fieldName; }

    String getFieldName(){return fieldName; }

    void setValuesFound(List<String>valuesFound){this.valuesFound = valuesFound; }

    List<String>getValuesFound(){return valuesFound; }

    void setTextEntry(String textEntry){this.textEntry = textEntry; }

    String getTextEntry(){return textEntry; }

    void setListEntry(List<String>listEntry){this.listEntry = listEntry; }

    List<String>getListEntry(){return listEntry; }

    void setList2Entry(List<String>list2Entry){this.list2Entry = list2Entry; }

    List<String>getList2Entry(){return list2Entry; }

    void setCheckEntry(String checkEntry){this.checkEntry = checkEntry; }

    String getCheckEntry(){return  checkEntry; };

    void setCheck2Entry(String check2Entry){this.check2Entry = check2Entry; }

    String getCheck2Entry(){return  check2Entry; };

    void setExample(String example){this.example = example; }

    String getExample(){return example; }

}
