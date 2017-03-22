package com.azquo.memorydb.service;

import com.azquo.TypedPair;
import com.azquo.memorydb.AzquoMemoryDBConnection;
import com.azquo.memorydb.DatabaseAccessToken;
import com.azquo.memorydb.TreeNode;
import com.azquo.memorydb.core.Name;
import com.azquo.memorydb.core.Provenance;
import com.azquo.memorydb.core.Value;
import com.azquo.memorydb.core.ValueHistory;
import com.azquo.memorydb.dao.ValueDAO;
import com.azquo.spreadsheet.AzquoCell;
import com.azquo.spreadsheet.DSSpreadsheetService;
import com.azquo.spreadsheet.DataRegionHeading;
import com.azquo.spreadsheet.ListOfValuesOrNamesAndAttributeName;
import com.azquo.spreadsheet.transport.ProvenanceDetailsForDisplay;
import com.azquo.spreadsheet.transport.ProvenanceForDisplay;

import java.text.DateFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Extracted from a few other service classes by edward on 13/10/16.
 * <p>
 * Factoring off some server side stuff related to provenance.
 */
public class ProvenanceService {

    // Some code was creating a temporary value but there's no such thing in Azquo so use this instead.
    private static class DummyValue {

        private final int id;

        private final String valueText;
        private final Collection<Name> names;

        private final List<String> history;

        DummyValue(int id, String valueText, Collection<Name> names, List<String> history) {
            this.id = id;
            this.valueText = valueText;
            this.names = names;
            this.history = history;
        }

        String getValueText() {
            return valueText;
        }

        public Collection<Name> getNames() {
            return names;
        }

        public int getId() {
            return id;
        }

        List<String> getHistory() {
            return history;
        }
    }

    // This will be changed to return a new object - ProvenanceDetailsForDisplay
    public static ProvenanceDetailsForDisplay getDataRegionProvenance(DatabaseAccessToken databaseAccessToken, List<List<String>> rowHeadingsSource
            , List<List<String>> colHeadingsSource, List<List<String>> contextSource, int unsortedRow, int unsortedCol, int maxSize) throws Exception {
        AzquoMemoryDBConnection azquoMemoryDBConnection = AzquoMemoryDBConnection.getConnectionFromAccessToken(databaseAccessToken);
        AzquoCell azquoCell = DSSpreadsheetService.getSingleCellFromRegion(azquoMemoryDBConnection, rowHeadingsSource, colHeadingsSource, contextSource, unsortedRow, unsortedCol, databaseAccessToken.getLanguages(), null);
        if (azquoCell != null) {
            final ListOfValuesOrNamesAndAttributeName valuesForCell = azquoCell.getListOfValuesOrNamesAndAttributeName();
            // todo, deal with name functions properly, will need to check through the DataRegionHeadings (as in don't just assume it's name count, could be something else)
            if (valuesForCell == null) {
                return nameCountProvenance(azquoCell);
            }
            if (valuesForCell.getValues() != null) {
                return valuesProvenance(valuesForCell.getValues(), maxSize);
            }
            // todo - in case of no row headings (import style data) this may NPE
            if (azquoCell.getRowHeadings().get(0).getAttribute() != null || azquoCell.getColumnHeadings().get(0).getAttribute() != null) {
                if (azquoCell.getRowHeadings().get(0).getAttribute() != null) { // then col name, row attribute
                    return attributeProvenance(azquoCell.getColumnHeadings().get(0).getName(), azquoCell.getRowHeadings().get(0).getAttribute());
                } else { // the other way around
                    return attributeProvenance(azquoCell.getRowHeadings().get(0).getName(), azquoCell.getColumnHeadings().get(0).getAttribute());
                }
            }
        }
        return new ProvenanceDetailsForDisplay(null,null); //just empty ok? null? Unsure
    }

    // might need to rewrite this and/or check variable names
    private static ProvenanceDetailsForDisplay nameCountProvenance(AzquoCell azquoCell) {
        String provString = "";
        Set<Name> cellNames = new HashSet<>();
        Name nameCountHeading = null;
        for (DataRegionHeading rowHeading : azquoCell.getRowHeadings()) {
            if (rowHeading != null){ // apparently it can be . . . is this a concern? Well NPE is no good, could error maggage on the else if this is a problem
                if (rowHeading.getFunction() == DataRegionHeading.FUNCTION.NAMECOUNT) {
                    provString += "namecount(" + rowHeading.getDescription();
                    nameCountHeading = rowHeading.getName();
                }
                if (rowHeading.getName() != null) {
                    cellNames.add(rowHeading.getName());
                }
            }
        }
        for (DataRegionHeading colHeading : azquoCell.getColumnHeadings()) {
            if (colHeading.getFunction() == DataRegionHeading.FUNCTION.NAMECOUNT) {
                provString += "namecount(" + colHeading.getDescription();
                nameCountHeading = colHeading.getName();
                break;
            }
            if (colHeading.getName() != null) {
                cellNames.add(colHeading.getName());
            }
        }
        if (nameCountHeading != null) {
            provString = "total" + provString;
        }
        Name cellName = cellNames.iterator().next();
        provString += " * " + cellName.getDefaultDisplayName() + ")";

        Provenance p = cellName.getProvenance();
        final ProvenanceForDisplay provenanceForDisplay = p.getProvenanceForDisplay();
        List<String> names = new ArrayList<>();
        if (azquoCell.getListOfValuesOrNamesAndAttributeName() != null && azquoCell.getListOfValuesOrNamesAndAttributeName().getNames() != null){
            for (Name n : azquoCell.getListOfValuesOrNamesAndAttributeName().getNames()){
                // this is a point, should it be in a language?
                names.add(n.getDefaultDisplayName());
            }
        }
        provenanceForDisplay.setNames(names);

/*        DateFormat df = new SimpleDateFormat("dd/MM/yy HH:mm");
        node.setValue(azquoCell.getDoubleValue() + "");
        node.setName(provString);
        String source = df.format(p.getTimeStamp()) + " by " + p.getUser();
        String method = p.getMethod();
        if (p.getName() != null) {
            method += " " + p.getName();
        }
        if (p.getContext() != null && p.getContext().length() > 1) method += " with " + p.getContext();
        node.setHeading(source + " " + method);*/
        return new ProvenanceDetailsForDisplay(provString,Collections.singletonList(provenanceForDisplay));
    }

    // logic will be changed for new object ProvenanceDetailsForDisplay
    // TODO - as mentioned, value history!
    private static ProvenanceDetailsForDisplay valuesProvenance(List<Value> values, int maxSize) {
        List<ProvenanceForDisplay> provenanceForDisplays = new ArrayList<>();
        if (values != null && (values.size() > 1 || (values.size() > 0 && values.get(0) != null))) {
            values.sort((o1, o2) -> (o2.getProvenance().getTimeStamp()).compareTo(o1.getProvenance().getTimeStamp()));
            //simply sending out values is a mess - hence this ruse: extract the most persistent names as headings
            List<Value> oneUpdate = new ArrayList<>();
            Provenance p = values.get(0).getProvenance();
            for (Value value : values) {
                if (value.getProvenance() == p) {// no need to check timestamps, just does the provenance match
                    oneUpdate.add(value);
                } else {
                    ProvenanceForDisplay provenanceForDisplay = p.getProvenanceForDisplay();
                    if (oneUpdate.size() > maxSize){
                        oneUpdate = oneUpdate.subList(0, maxSize);
                    }
                    provenanceForDisplay.setValuesWithIdsAndNames(getIdValuesWithIdsAndNames(oneUpdate)); // todo - value history . . .
                    provenanceForDisplays.add(provenanceForDisplay);
                    oneUpdate = new ArrayList<>();
                    oneUpdate.add(value);
                    p = value.getProvenance();
                }
            }
            ProvenanceForDisplay provenanceForDisplay = p.getProvenanceForDisplay();
            if (oneUpdate.size() > maxSize){
                oneUpdate = oneUpdate.subList(0, maxSize);
            }
            provenanceForDisplay.setValuesWithIdsAndNames(getIdValuesWithIdsAndNames(oneUpdate)); // todo - value history . . .
            provenanceForDisplays.add(provenanceForDisplay);
        }
        return new ProvenanceDetailsForDisplay(null, provenanceForDisplays);
    }

    // first string is the value, then the names . . .
    private static List<TypedPair<Integer, List<String>>> getIdValuesWithIdsAndNames(List<Value> values){
        List<TypedPair<Integer, List<String>>> toReturn = new ArrayList<>();
        for (Value v : values){
            List<String> valueAndNames = new ArrayList<>();
            valueAndNames.add(v.getText());
            // don't order names yet, think about that later
            for (Name n : v.getNames()){
                valueAndNames.add(n.getDefaultDisplayName());
            }
            toReturn.add(new TypedPair<>(v.getId(), valueAndNames));
        }
        return toReturn;
    }


    private static DateFormat df = new SimpleDateFormat("dd/MM/yy HH:mm");

    private static ProvenanceDetailsForDisplay attributeProvenance(Name name, String attribute) {
        attribute = attribute.substring(1).replace("`", "");
        String description = name.getDefaultDisplayName() + "." + attribute;
        ProvenanceForDisplay provenanceForDisplay = name.getProvenance().getProvenanceForDisplay();
        provenanceForDisplay.setNames(Collections.singletonList(name.getDefaultDisplayName()));
        return new ProvenanceDetailsForDisplay(description, Collections.singletonList(provenanceForDisplay));
    }


    /* valuesProvenance the values. It finds the name which represents the most values and displays
    them under them then the name that best represents the rest etc etc until all values have been displayed
    For inspecting databases
      */
    private static TreeNode getTreeNode(AzquoMemoryDBConnection azquoMemoryDBConnection, Set<Value> values, Provenance p, int maxSize) {
        String source = df.format(p.getTimeStamp()) + " by " + p.getUser();
        String method = p.getMethod();
        if (p.getName() != null) {
            method += " " + p.getName();
        }
        if (p.getContext() != null && p.getContext().length() > 1) method += " with " + p.getContext();
        TreeNode toReturn = new TreeNode(source, method, null, 0, getTreeNodesFromValues(azquoMemoryDBConnection, values, maxSize));
        addNodeValues(toReturn);
        return toReturn;
    }

    private static List<TreeNode> getTreeNodesFromValues(AzquoMemoryDBConnection azquoMemoryDBConnection, Set<Value> values, int maxSize) {
        Set<DummyValue> convertedToDummy = new HashSet<>(values.size());
        for (Value value : values) {
            // I think it is this one right here that can overload the connections
            final List<ValueHistory> historyForValue = ValueDAO.getHistoryForValue(azquoMemoryDBConnection.getAzquoMemoryDB(), value);
            List<String> history = new ArrayList<>();
            for (ValueHistory vh : historyForValue) {
                String provenance = null;
                if (vh.getProvenance() != null) {
                    provenance = df.format(vh.getProvenance().getTimeStamp()) + " by " + vh.getProvenance().getUser();
                    provenance += " ";
                    provenance += vh.getProvenance().getMethod();
                    if (vh.getProvenance().getName() != null) {
                        provenance += " " + vh.getProvenance().getName();
                    }
                    if (vh.getProvenance().getContext() != null && vh.getProvenance().getContext().length() > 1)
                        provenance += " with " + vh.getProvenance().getContext();
                }
                history.add(vh.getText() + "\t" + (provenance != null ? (" " + provenance) : ""));
            }
            convertedToDummy.add(new DummyValue(value.getId(), value.getText(), value.getNames(), history.size() > 0 ? history : null));
        }
        return getTreeNodesFromDummyValues(convertedToDummy, maxSize);
    }

    private static AtomicInteger getTreeNodesFromValues2Count = new AtomicInteger(0);

    private static List<TreeNode> getTreeNodesFromDummyValues(Set<DummyValue> values, int maxSize) {
        getTreeNodesFromValues2Count.incrementAndGet();
        //int debugCount = 0;
        boolean headingNeeded = false;
        double dValue = 0.0;
        List<TreeNode> nodeList = new ArrayList<>();
        int count = 0;
        for (DummyValue value : values) {
            if (value.getNames().size() > 1) {
                headingNeeded = true;
                break;
            }
            count++;
            if (count > maxSize) {
                nodeList.add(new TreeNode((values.size() - maxSize) + " more...", "", 0, 0, null));
                break;
            }
            String nameFound = null;
            for (Name name : value.getNames()) {
                nameFound = name.getDefaultDisplayName(); // so it's always going to be the last name??
            }
            String val = value.getValueText();
            double d = 0;
            try {
                d = Double.parseDouble(val);
                if (d != 0) {
                    val = roundValue(d);
                }
            } catch (Exception ignored) {
            }
            nodeList.add(new TreeNode(nameFound, val, d, value.getId(), value.getHistory()));
        }
        if (headingNeeded) {
            Name topParent = null;
            while (values.size() > 0) {
                count++;
                Name heading = getMostUsedName(values, topParent);
                topParent = heading.findATopParent();
                Set<DummyValue> extract = new HashSet<>();
                Set<DummyValue> slimExtract = new HashSet<>();
                for (DummyValue value : values) {
                    if (value.getNames().contains(heading)) {
                        extract.add(value);
                        try {
                            dValue += Double.parseDouble(value.getValueText());
                        } catch (Exception e) {
                            //ignore
                        }
                        //creating a new 'value' with one less name for recursion
                        try {
                            Set<Name> slimNames = new HashSet<>(value.getNames());
                            slimNames.remove(heading);
                            DummyValue slimValue = new DummyValue(value.getId(), value.getValueText(), slimNames, value.getHistory());
                            slimExtract.add(slimValue);
                        } catch (Exception e) {
                            // exception from value constructor, should not happen
                            e.printStackTrace();
                        }
                        //debugCount = slimValue.getNames().size();
                    }
                }
                values.removeAll(extract);
                nodeList.add(new TreeNode("", heading.getDefaultDisplayName(), roundValue(dValue), dValue, getTreeNodesFromDummyValues(slimExtract, maxSize)));
                dValue = 0;
            }
        }
        return nodeList;

    }

    // find the most used name by a set of values, used by printBatch to derive headings

    private static Name getMostUsedName(Set<DummyValue> values, Name topParent) {
        Map<Name, Integer> nameCount = new HashMap<>();
        for (DummyValue value : values) {
            for (Name name : value.getNames()) {
                if (topParent == null || name.findATopParent() == topParent) {
                    Integer origCount = nameCount.get(name);
                    if (origCount == null) {
                        nameCount.put(name, 1);
                    } else {
                        nameCount.put(name, origCount + 1);
                    }
                }
            }
        }
        if (nameCount.size() == 0) {
            return getMostUsedName(values, null);
        }
        int maxCount = 0;
        Name maxName = null;
        for (Name name : nameCount.keySet()) {
            int count = nameCount.get(name);
            if (count > maxCount) {
                maxCount = count;
                maxName = name;
            }
        }
        return maxName;
    }

    public static void addNodeValues(TreeNode t) {
        double d = 0;
        for (TreeNode child : t.getChildren()) {
            d += child.getDvalue();
        }
        t.setValue(roundValue(d));
        t.setDvalue(d);
    }

    private static String roundValue(double dValue) {
        Locale locale = Locale.getDefault();
        NumberFormat nf = NumberFormat.getInstance(locale);
        // is other formatting required?
        return nf.format(dValue);
    }

    public static List<TreeNode> nodify(AzquoMemoryDBConnection azquoMemoryDBConnection, List<Value> values, int maxSize) {
        List<TreeNode> toReturn = new ArrayList<>();
        if (values != null && (values.size() > 1 || (values.size() > 0 && values.get(0) != null))) {
            values.sort((o1, o2) -> (o2.getProvenance().getTimeStamp()).compareTo(o1.getProvenance().getTimeStamp()));
            //simply sending out values is a mess - hence this ruse: extract the most persistent names as headings
            Date provdate = values.get(0).getProvenance().getTimeStamp();
            Set<Value> oneUpdate = new HashSet<>();
            Provenance p = null;
            for (Value value : values) {
                if (value.getProvenance().getTimeStamp() == provdate) {
                    oneUpdate.add(value);
                    p = value.getProvenance();
                } else {
                    toReturn.add(ProvenanceService.getTreeNode(azquoMemoryDBConnection, oneUpdate, p, maxSize));
                    oneUpdate = new HashSet<>();
                    oneUpdate.add(value);
                    p = value.getProvenance();
                    provdate = value.getProvenance().getTimeStamp();
                }
            }
            toReturn.add(ProvenanceService.getTreeNode(azquoMemoryDBConnection, oneUpdate, p, maxSize));
        }
        return toReturn;
    }

}