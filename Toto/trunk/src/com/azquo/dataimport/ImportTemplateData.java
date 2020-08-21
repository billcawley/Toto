package com.azquo.dataimport;

import org.apache.poi.ss.util.AreaReference;

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
        String nameFormula = names.get(nameName.trim().toLowerCase());
        if (nameFormula != null){
            return new AreaReference(nameFormula, null);
        }
        return null;
    }

    // note this highlights a flaw with the current map, it doesn't deal with names with the same name on different sheets TODO
    public AreaReference getName(String nameName, String sheetName) {
        String nameFormula = names.get(nameName.trim().toLowerCase());
        if (nameFormula != null && nameFormula.contains("!") && nameFormula.substring(0, nameFormula.indexOf("!")).equalsIgnoreCase(sheetName)){
            return new AreaReference(nameFormula, null);
        }
        return null;
    }

    public Map<String, AreaReference> getNamesForSheet(String sheetName) {
        Map<String, AreaReference> toReturn = new HashMap<>();
        for (Map.Entry<String, String> entry : names.entrySet()){
            if (entry.getValue().contains("!") && entry.getValue().substring(0, entry.getValue().indexOf("!")).equalsIgnoreCase(sheetName)){
                toReturn.put(entry.getKey(), new AreaReference(entry.getValue(), null));
            }
        }
        return toReturn;
    }
}
