package com.azquo.memorydb.service;

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
import com.azquo.spreadsheet.transport.RegionOptions;
import com.azquo.spreadsheet.transport.ValueDetailsForProvenance;

import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
    public static ProvenanceDetailsForDisplay getDataRegionProvenance(DatabaseAccessToken databaseAccessToken, String user, List<List<String>> rowHeadingsSource
            , List<List<String>> colHeadingsSource, List<List<String>> contextSource, RegionOptions regionOptionsForTransport, int unsortedRow, int unsortedCol, int maxSize) throws Exception {
        AzquoMemoryDBConnection azquoMemoryDBConnection = AzquoMemoryDBConnection.getConnectionFromAccessToken(databaseAccessToken);
        AzquoCell azquoCell = DSSpreadsheetService.getSingleCellFromRegion(azquoMemoryDBConnection, rowHeadingsSource, colHeadingsSource, contextSource, regionOptionsForTransport, unsortedRow, unsortedCol, user, null);
        if (azquoCell != null) {
            final ListOfValuesOrNamesAndAttributeName valuesForCell = azquoCell.getListOfValuesOrNamesAndAttributeName();
            // todo, deal with name functions properly, will need to check through the DataRegionHeadings (as in don't just assume it's name count, could be something else) also valuse set
            if (valuesForCell == null) {
                return nameCountProvenance(azquoCell);
            }
            if (valuesForCell.getValues() != null) {
                return valuesProvenance(azquoMemoryDBConnection, valuesForCell.getValues(), azquoCell, maxSize);
            }
            // todo - in case of no row headings (import style data) this may NPE
            // get the last not the first when looking for attributes, first ones could be null
            if (azquoCell.getRowHeadings().get(azquoCell.getRowHeadings().size() - 1).getAttribute() != null || azquoCell.getColumnHeadings().get(azquoCell.getColumnHeadings().size() - 1).getAttribute() != null) {
                if (azquoCell.getRowHeadings().get(azquoCell.getRowHeadings().size() - 1).getAttribute() != null) { // then col name, row attribute
                    return attributeProvenance(azquoCell.getColumnHeadings().get(azquoCell.getColumnHeadings().size() - 1).getName(), azquoCell.getRowHeadings().get(azquoCell.getRowHeadings().size() - 1).getAttribute());
                } else { // the other way around
                    return attributeProvenance(azquoCell.getRowHeadings().get(azquoCell.getRowHeadings().size() - 1).getName(), azquoCell.getColumnHeadings().get(azquoCell.getColumnHeadings().size() - 1).getAttribute());
                }
            }
        }

        return new ProvenanceDetailsForDisplay("Audit not found", null, null); //just empty ok? null? Unsure
    }

    // might need to rewrite this and/or check variable names
    // todo make generic for the expression provenance but what should it show???
    private static ProvenanceDetailsForDisplay nameCountProvenance(AzquoCell azquoCell) {
        StringBuilder provString = new StringBuilder();
        Set<Name> cellNames = new HashSet<>();
        Name nameCountHeading = null;
        for (DataRegionHeading rowHeading : azquoCell.getRowHeadings()) {
            if (rowHeading != null) { // apparently it can be . . . is this a concern? Well NPE is no good, could error maggage on the else if this is a problem
                if (rowHeading.getFunction() == DataRegionHeading.FUNCTION.NAMECOUNT  || rowHeading.getFunction() == DataRegionHeading.FUNCTION.NAMELIST || rowHeading.getFunction() == DataRegionHeading.FUNCTION.EXISTS)  {
                    provString.append(rowHeading.getFunction() + "(").append(rowHeading.getStringParameter());
                    nameCountHeading = rowHeading.getName();
                }
                if (rowHeading.getName() != null) {
                    cellNames.add(rowHeading.getName());
                }
            }
        }
        for (DataRegionHeading colHeading : azquoCell.getColumnHeadings()) {
            if (colHeading == null){
                System.out.println("null column heading, column headings " + azquoCell.getColumnHeadings());
                System.out.println("row headings " + azquoCell.getRowHeadings());
                System.out.println("context " + azquoCell.getContexts());
            }
            if (colHeading.getFunction() == DataRegionHeading.FUNCTION.NAMECOUNT || colHeading.getFunction()==DataRegionHeading.FUNCTION.NAMELIST || colHeading.getFunction() == DataRegionHeading.FUNCTION.EXISTS) {
                provString.append(colHeading.getFunction()+"(").append(colHeading.getStringParameter());
                nameCountHeading = colHeading.getName();
                break;
            }
            if (colHeading.getName() != null) {
                cellNames.add(colHeading.getName());
            }
        }
        if (nameCountHeading != null) {
            provString.insert(0, "total");
        }
        Name cellName = cellNames.iterator().next();
        provString.append(" * ").append(cellName.getDefaultDisplayName()).append(")");

        Provenance p = cellName.getProvenance();
        final ProvenanceForDisplay provenanceForDisplay = p.getProvenanceForDisplay();
        List<String> names = new ArrayList<>();
        if (azquoCell.getListOfValuesOrNamesAndAttributeName() != null && azquoCell.getListOfValuesOrNamesAndAttributeName().getNames() != null) {
            for (Name n : azquoCell.getListOfValuesOrNamesAndAttributeName().getNames()) {
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
        return new ProvenanceDetailsForDisplay(getProvenanceHeadlineForCell(azquoCell), provString.toString(), Collections.singletonList(provenanceForDisplay));
    }

    /*
    most of the time provenance relates to a cell, this will make a suitable headine for display
     */

    static private String getProvenanceHeadlineForCell(AzquoCell azquoCell) {
        StringBuilder toReturn = new StringBuilder();
        toReturn.append(checkNumberFormat(azquoCell.getStringValue()));
        for (DataRegionHeading context : azquoCell.getContexts()) {
            toReturn.append(", ").append(context.getName() != null ? context.getName().getDefaultDisplayName() : context.getStringParameter());
        }
        for (DataRegionHeading rowHeading : azquoCell.getRowHeadings()) {
            if (rowHeading != null) {
                toReturn.append(", ").append(rowHeading.getName() != null ? rowHeading.getName().getDefaultDisplayName() : rowHeading.getStringParameter());
            }
        }
        for (DataRegionHeading columnHeading : azquoCell.getColumnHeadings()) {
            // could be a blank column heading, a gap on multi line
            if (columnHeading != null) {
                toReturn.append(", ").append(columnHeading.getName() != null ? columnHeading.getName().getDefaultDisplayName() : columnHeading.getStringParameter());
            }
        }
        return toReturn.toString();
    }

    /* logic will be changed for new object ProvenanceDetailsForDisplay
     TODO - as mentioned, value history!
     */

    public static ProvenanceDetailsForDisplay getListOfChangedValues(AzquoMemoryDBConnection azquoMemoryDBConnection, int maxSize) {
        return valuesProvenance(azquoMemoryDBConnection, azquoMemoryDBConnection.getValuesChanged(), null, maxSize);
    }


    private static ProvenanceDetailsForDisplay valuesProvenance(AzquoMemoryDBConnection azquoMemoryDBConnection, List<Value> values, AzquoCell azquoCell, int maxSize) {
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
                    if (oneUpdate.size() > maxSize) {
                        oneUpdate = oneUpdate.subList(0, maxSize);
                    }
                    provenanceForDisplay.setValueDetailsForProvenances(getValueDetailsForProvenances(azquoMemoryDBConnection, oneUpdate));
                    provenanceForDisplays.add(provenanceForDisplay);
                    oneUpdate = new ArrayList<>();
                    oneUpdate.add(value);
                    p = value.getProvenance();
                }
            }
            ProvenanceForDisplay provenanceForDisplay = p.getProvenanceForDisplay();
            if (oneUpdate.size() > maxSize) {
                oneUpdate = oneUpdate.subList(0, maxSize);
            }
            provenanceForDisplay.setValueDetailsForProvenances(getValueDetailsForProvenances(azquoMemoryDBConnection, oneUpdate));
            provenanceForDisplays.add(provenanceForDisplay);
        }
        return new ProvenanceDetailsForDisplay(azquoCell != null ? getProvenanceHeadlineForCell(azquoCell) : "", null, provenanceForDisplays);
    }

    // first string is the value, then the names . . .
    // needs the connection to check for historic values
    private static List<ValueDetailsForProvenance> getValueDetailsForProvenances(AzquoMemoryDBConnection azquoMemoryDBConnection, List<Value> values) {
        List<ValueDetailsForProvenance> toReturn = new ArrayList<>();
        for (Value v : values) {
            List<Name> namesList = new ArrayList<>(v.getNames());
            namesList.sort(Comparator.comparingInt(Name::getValueCount));
            List<String> nameStrings = new ArrayList<>();
            for (Name n : namesList) {
                nameStrings.add(n.getDefaultDisplayName());
            }
            // now search for value history
            final List<ValueHistory> historyForValue = ValueDAO.getHistoryForValue(azquoMemoryDBConnection.getAzquoMemoryDB(), v);
            List<ValueDetailsForProvenance.HistoricValueAndProvenance> historicValuesAndProvenance = new ArrayList<>();
            for (ValueHistory valueHistory : historyForValue) {
                historicValuesAndProvenance.add(new ValueDetailsForProvenance.HistoricValueAndProvenance(checkNumberFormat(valueHistory.getText()), valueHistory.getProvenance().getProvenanceForDisplay().toString()));
            }
            toReturn.add(new ValueDetailsForProvenance(v.getId(), checkNumberFormat(v.getText()), nameStrings, historicValuesAndProvenance));
        }
        return toReturn;
    }

    private static String checkNumberFormat(String text) {
        try {
            double asNumber = Double.parseDouble(text);
            NumberFormat instance = NumberFormat.getInstance();
            if (text.length() >= 10) {
                instance.setMaximumFractionDigits(2);
            }
            text = instance.format(asNumber);
        } catch (Exception ignored) {
        }
        return text;
    }


    private static DateTimeFormatter df = DateTimeFormatter.ofPattern("dd/MM/yy HH:mm");

    private static ProvenanceDetailsForDisplay attributeProvenance(Name name, String attribute) {
        attribute = attribute.substring(1).replace("`", "");
        String description = name.getDefaultDisplayName() + "." + attribute;
        ProvenanceForDisplay provenanceForDisplay = name.getProvenance().getProvenanceForDisplay();
        provenanceForDisplay.setNames(Collections.singletonList(name.getDefaultDisplayName()));
        return new ProvenanceDetailsForDisplay(attribute, description, Collections.singletonList(provenanceForDisplay));
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
        if (values.size()==1 && values.iterator().next().getText().length()>0){
            toReturn.setValue(values.iterator().next().getText());
        }
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
            if (nameFound!=null){
                nodeList.add(new TreeNode(nameFound, val, d, value.getId(), value.getHistory()));
            }
        }
        if (headingNeeded) {
            Name topParent = null;
            Collection<Name> commonNames = new ArrayList<>();
            commonNames.addAll(values.iterator().next().names);
            for (DummyValue value:values){
                commonNames.retainAll(value.getNames());
                if (commonNames.size()==0){
                    break;
                }
            }
            Set<DummyValue> slimExtract = new HashSet<>();
            if (commonNames.size()==0) {
                while (values.size() > 0) {
                    count++;
                    Name heading = getMostUsedName(values, topParent);
                    topParent = heading.findATopParent();
                    Set<DummyValue> extract = new HashSet<>();
                    slimExtract = new HashSet<>();
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
                    String headingName = heading.getDefaultDisplayName();
                    nodeList.add(new TreeNode("", headingName, roundValue(dValue), dValue, getTreeNodesFromDummyValues(slimExtract, maxSize)));
                    dValue = 0;
                }
            }else{
                String headingName = null;
                for (Name name:commonNames) {
                    if (headingName == null) {
                        headingName = name.getDefaultDisplayName();
                    } else {
                        headingName += "|" + name.getDefaultDisplayName();
                    }
                }
                for (DummyValue value : values) {
                    try {
                        dValue += Double.parseDouble(value.getValueText());
                    } catch (Exception e) {
                        //ignore
                    }
                    //creating a new 'value' without the common names for recursion
                    try {
                        Set<Name> slimNames = new HashSet<>(value.getNames());
                        slimNames.removeAll(commonNames);
                        DummyValue slimValue = new DummyValue(value.getId(), value.getValueText(), slimNames, value.getHistory());
                        slimExtract.add(slimValue);
                    } catch (Exception e) {
                        // exception from value constructor, should not happen
                        e.printStackTrace();
                    }
                    //debugCount = slimValue.getNames().size();
                }
                nodeList.add(new TreeNode("", headingName, roundValue(dValue), dValue, getTreeNodesFromDummyValues(slimExtract, maxSize)));

            }
        }
        return nodeList;

    }

    // find the most used name by a set of values, used by printBatch to derive headings

    private static Name getMostUsedName(Set<DummyValue> values, Name topParent) {
        Map<Name, Integer> nameCounts = new HashMap<>();
        for (DummyValue value : values) {
            for (Name name : value.getNames()) {
                if (topParent == null || name.findATopParent() == topParent) {
                    nameCounts.merge(name, 1, (a, b) -> a + b);
                }
            }
        }
        if (nameCounts.size() == 0) {
            return getMostUsedName(values, null);
        }
        int maxCount = 0;
        Name maxName = null;
        for (Map.Entry<Name, Integer> nameCount : nameCounts.entrySet()) {
            int count = nameCount.getValue();
            if (count > maxCount) {
                maxCount = count;
                maxName = nameCount.getKey();
            }
        }
        return maxName;
    }

    public static void addNodeValues(TreeNode t) {
        if (t.getChildren().size()==1){
            TreeNode child = t.getChildren().get(0);
            t.setValue(child.getValue());
            t.setDvalue(child.getDvalue());
            return;
        }
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
            LocalDateTime provdate = values.get(0).getProvenance().getTimeStamp();
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