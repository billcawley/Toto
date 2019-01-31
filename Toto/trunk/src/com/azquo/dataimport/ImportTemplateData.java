package com.azquo.dataimport;

import org.zkoss.poi.ss.util.AreaReference;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ImportTemplateData {

    private final Map<String, List<List<String>>> sheets;

    private final Map<String, String> names;

    public ImportTemplateData() {
        sheets = new HashMap<>();
        names = new HashMap<>();
    }

    public Map<String, List<List<String>>> getSheets() {
        return sheets;
    }

    public void putName(String nameName, String textContent) {
        names.put(nameName.trim().toLowerCase(), textContent);
    }

    public AreaReference getName(String nameName) {
        String nameForumla = names.get(nameName.trim().toLowerCase());
        if (nameForumla != null){
            return new AreaReference(nameForumla);
        }
        return null;
    }


}
