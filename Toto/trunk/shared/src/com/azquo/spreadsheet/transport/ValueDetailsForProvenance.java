package com.azquo.spreadsheet.transport;

/*
Created 25/09/2018 by EFC. Improving the structure of audit details sent to the UI, principally
to help with displaying ValueHistory more correctly.
 */

import com.azquo.TypedPair;

import java.io.Serializable;
import java.util.List;

public class ValueDetailsForProvenance implements Serializable {
    private final int id;
    private final String valueText;
    private final List<String> names;
    private final List<TypedPair<String, String>> historicValuesAndProvenance;

    public ValueDetailsForProvenance(int id, String valueText, List<String> names, List<TypedPair<String, String>> historicValuesAndProvenance) {
        this.id = id;
        this.valueText = valueText;
        this.names = names;
        this.historicValuesAndProvenance = historicValuesAndProvenance;
    }

    public int getId() {
        return id;
    }

    public String getValueTextForDisplay() {
        return valueText;
    }

    public List<String> getNames() {
        return names;
    }

    public List<TypedPair<String, String>> getHistoricValuesAndProvenance() {
        return historicValuesAndProvenance;
    }
}
