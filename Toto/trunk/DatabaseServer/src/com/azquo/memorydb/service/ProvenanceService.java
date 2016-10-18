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

import java.text.DateFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Extracted from a few other service classes by edward on 13/10/16.
 *
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

    // ok this should NOPT return tree nodes, they should just be for JSTree, ergh!

    public static List<TreeNode> getDataRegionProvenance(DatabaseAccessToken databaseAccessToken, List<List<String>> rowHeadingsSource
            , List<List<String>> colHeadingsSource, List<List<String>> contextSource, int unsortedRow, int unsortedCol, int maxSize) throws Exception {
        AzquoMemoryDBConnection azquoMemoryDBConnection = DSSpreadsheetService.getConnectionFromAccessToken(databaseAccessToken);
        AzquoCell azquoCell = DSSpreadsheetService.getSingleCellFromRegion(azquoMemoryDBConnection, rowHeadingsSource, colHeadingsSource, contextSource, unsortedRow, unsortedCol, databaseAccessToken.getLanguages());
        if (azquoCell != null) {
            final ListOfValuesOrNamesAndAttributeName valuesForCell = azquoCell.getListOfValuesOrNamesAndAttributeName();
            //Set<Name> specialForProvenance = new HashSet<Name>();
            // todo, deal with name functions properly, will need to check through the DataRegionHeadings
            if (valuesForCell == null) {
                return nameCountProvenance(azquoCell);
            }
            if (valuesForCell.getValues() != null) {
                return nodify(azquoMemoryDBConnection, valuesForCell.getValues(), maxSize);
            }
            // todo - in case of now row headings ( import style data) this may NPE
            if (azquoCell.getRowHeadings().get(0).getAttribute() != null || azquoCell.getColumnHeadings().get(0).getAttribute() != null) {
                if (azquoCell.getRowHeadings().get(0).getAttribute() != null) { // then col name, row attribute
                    return nodify(azquoCell.getColumnHeadings().get(0).getName(), azquoCell.getRowHeadings().get(0).getAttribute());
                } else { // the other way around
                    return nodify(azquoCell.getRowHeadings().get(0).getName(), azquoCell.getColumnHeadings().get(0).getAttribute());
                }
            }
        }
        return Collections.emptyList(); //just empty ok? null? Unsure
    }

    private static List<TreeNode> nameCountProvenance(AzquoCell azquoCell) {
        String provString = "";
        Set<Name> cellNames = new HashSet<>();
        Name nameCountHeading = null;
        for (DataRegionHeading rowHeading : azquoCell.getRowHeadings()) {
            if (rowHeading.getFunction() == DataRegionHeading.FUNCTION.NAMECOUNT) {
                provString += "namecount(" + rowHeading.getDescription();
                nameCountHeading = rowHeading.getName();
            }
            if (rowHeading.getName() != null) {
                cellNames.add(rowHeading.getName());
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
        List<TreeNode> toReturn = new ArrayList<>();
        if (nameCountHeading != null) {
            provString = "total" + provString;
        }
        Name cellName = cellNames.iterator().next();
        provString += " * " + cellName.getDefaultDisplayName() + ")";
        DateFormat df = new SimpleDateFormat("dd/MM/yy HH:mm");
        Provenance p = cellName.getProvenance();
        TreeNode node = new TreeNode();
        node.setValue(azquoCell.getDoubleValue() + "");
        node.setName(provString);
        String source = df.format(p.getTimeStamp()) + " by " + p.getUser();
        String method = p.getMethod();
        if (p.getName() != null) {
            method += " " + p.getName();
        }
        if (p.getContext() != null && p.getContext().length() > 1) method += " with " + p.getContext();
        node.setHeading(source + " " + method);
        toReturn.add(node);
        return toReturn;
    }

    // As I understand this function is showing names attached to the values in this cell that are not in the requesting spread sheet's row/column/context
    // for provenance?
    public static List<TreeNode> nodify(AzquoMemoryDBConnection azquoMemoryDBConnection, List<Value> values, int maxSize) {
        List<TreeNode> toReturn = new ArrayList<>();
        if (values != null && (values.size() > 1 || (values.size() > 0 && values.get(0) != null))) {
            sortValues(values);
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

    // by time. For database inspect.
    static void sortValues(List<Value> values) {
        Collections.sort(values, (o1, o2) ->
        {
            if (o1.getProvenance().getTimeStamp() == null && o2.getProvenance().getTimeStamp() == null){
                return 0;
            }
            // check this is the right way around later
            if (o1.getProvenance().getTimeStamp() == null) {
                return -1;
            }
            if (o2.getProvenance().getTimeStamp() == null) {
                return 1;
            }
            return (o2.getProvenance().getTimeStamp())
                    .compareTo(o1.getProvenance().getTimeStamp());
        });
    }

    private static DateFormat df = new SimpleDateFormat("dd/MM/yy HH:mm");

    // another not very helpfully named function, might be able to be rewritten after we zap Azquo Book (the Aspose based functionality)
    private static List<TreeNode> nodify(Name name, String attribute) {
        attribute = attribute.substring(1).replace("`", "");
        List<TreeNode> toReturn = new ArrayList<>();
        DateFormat df = new SimpleDateFormat("dd/MM/yy HH:mm");
        Provenance p = name.getProvenance();
        TreeNode node = new TreeNode();
        node.setValue(name.getAttribute(attribute));
        node.setName(name.getDefaultDisplayName() + "." + attribute);
        String source = df.format(p.getTimeStamp()) + " by " + p.getUser();
        String method = p.getMethod();
        if (p.getName() != null) {
            method += " " + p.getName();
        }
        if (p.getContext() != null && p.getContext().length() > 1) method += " with " + p.getContext();
        node.setHeading(source + " " + method);
        toReturn.add(node);
        return toReturn;
    }


    /* nodify the values. It finds the name which represents the most values and displays
    them under them then the name that best represents the rest etc etc until all values have been displayed
    For inspecting databases
      */
    private static TreeNode getTreeNode(AzquoMemoryDBConnection azquoMemoryDBConnection, Set<Value> values, Provenance p, int maxSize) {
        String source = (p.getTimeStamp() != null ? df.format(p.getTimeStamp()) : "date unknown") + " by " + p.getUser();
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
            final List<ValueHistory> historyForValue = ValueDAO.getHistoryForValue(azquoMemoryDBConnection.getAzquoMemoryDB(), value);
            List<String> history = new ArrayList<>();
            for (ValueHistory vh : historyForValue){
                String provenance = null;
                if (vh.getProvenance() != null){
                    provenance = (vh.getProvenance().getTimeStamp() != null ? df.format(vh.getProvenance().getTimeStamp()) : "date unknown") + " by " + vh.getProvenance().getUser();
                    provenance += " ";
                    provenance += vh.getProvenance().getMethod();
                    if (vh.getProvenance().getName() != null) {
                        provenance += " " + vh.getProvenance().getName();
                    }
                    if (vh.getProvenance().getContext() != null && vh.getProvenance().getContext().length() > 1) provenance += " with " + vh.getProvenance().getContext();

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
}