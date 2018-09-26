package com.azquo.spreadsheet.transport;

import com.azquo.TypedPair;

import java.io.Serializable;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;

/**
 * Created by edward on 18/01/17.
 * the names or values that make up the cell are grouped into provenance
 * More mutable than it was at first as ProvenanceForDisplay can be converted by an attribute against the import spec.
 * see checkProvenanceForDisplayForAuditSheet in ProvenanceService.
 *
 */
public class ProvenanceForDisplay implements Serializable {
    private boolean inSpreadsheet;
    private final String user;
    private String method;
    private String name;
    private String context;
    private final LocalDateTime date;
     // now what can be attached to each provenance in this context?
    private List<ValueDetailsForProvenance> valueDetailsForProvenances;
    private List<String> names;

    public ProvenanceForDisplay(boolean inSpreadsheet, String user, String method, String name, String context, LocalDateTime date ) {
        this.inSpreadsheet = inSpreadsheet;
        this.user = user;
        this.method = method;
        this.name = name;
        this.context = context;
        this.date = date;
        names = null;
        valueDetailsForProvenances = null;
    }

    public void setInSpreadsheet(boolean inSpreadsheet) {
        this.inSpreadsheet = inSpreadsheet;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setContext(String context) {
        this.context = context;
    }

    public boolean isInSpreadsheet() {
        return inSpreadsheet;
    }

    public String getUser() {
        return user;
    }

    public String getMethod() {
        return method;
    }

    public String getName() {
        return name;
    }

    public LocalDateTime getDate() {return date; }

    public String getContext() {
        return context;
    }

    public List<String> getNames() {
        return names;
    }

    public void setNames(List<String> names) {
        this.names = names;
    }

    public List<ValueDetailsForProvenance> getValueDetailsForProvenances() {
        return valueDetailsForProvenances;
    }

    public void setValueDetailsForProvenances(List<ValueDetailsForProvenance> valueDetailsForProvenances) {
        this.valueDetailsForProvenances = valueDetailsForProvenances;
    }

    private final static DateTimeFormatter df = DateTimeFormatter.ofPattern("dd/MM/yy HH:mm");

    @Override
    public String toString() {
        StringBuilder toReturn =  new StringBuilder();
        toReturn.append(df.format(date)).append(" by ").append(user);
        toReturn.append(" ").append(method);
        if (name != null){
            toReturn.append(" ").append(name);
        }
        if (context != null && context.length() > 0){
            toReturn.append(" with ").append(context);
        }
        return toReturn.toString();
    }
}