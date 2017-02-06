package com.azquo.spreadsheet.transport;

import com.azquo.TypedPair;

import java.io.Serializable;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * Created by edward on 18/01/17.
 * the names or values that make up the cell are grouped into provenance
 */
public class ProvenanceForDisplay implements Serializable {
    private final boolean inSpreadsheet;
    private final String user;
    private final String method;
    private final String name;
    private final String context;
    private final Date date;
    // now what can be attached to each provenance in this context?
    private List<TypedPair<Integer, List<String>>> valuesWithIdsAndNames;
    private List<String> names;

    public ProvenanceForDisplay(boolean inSpreadsheet, String user, String method, String name, String context, Date date) {
        this.inSpreadsheet = inSpreadsheet;
        this.user = user;
        this.method = method;
        this.name = name;
        this.context = context;
        this.date = date;
        names = null;
        valuesWithIdsAndNames = null;
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

    public String getContext() {
        return context;
    }

    public List<String> getNames() {
        return names;
    }

    public void setNames(List<String> names) {
        this.names = names;
    }

    public List<TypedPair<Integer, List<String>>> getValuesWithIdsAndNames() {
        return valuesWithIdsAndNames;
    }

    public void setValuesWithIdsAndNames(List<TypedPair<Integer, List<String>>> valuesWithIdsAndNames) {
        this.valuesWithIdsAndNames = valuesWithIdsAndNames;
    }

    private static DateFormat df = new SimpleDateFormat("dd/MM/yy HH:mm");

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