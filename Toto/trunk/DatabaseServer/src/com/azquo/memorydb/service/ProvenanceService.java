package com.azquo.memorydb.service;

import com.azquo.TypedPair;
import com.azquo.dataimport.ValuesImport;
import com.azquo.memorydb.AzquoMemoryDBConnection;
import com.azquo.memorydb.Constants;
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
import net.openhft.koloboke.collect.set.hash.HashObjSets;

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
                return valuesProvenance(azquoMemoryDBConnection, valuesForCell.getValues(), maxSize);
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
        return new ProvenanceDetailsForDisplay(null, null); //just empty ok? null? Unsure
    }

    // might need to rewrite this and/or check variable names
    // todo make generic for the expression provenance but what should it show???
    private static ProvenanceDetailsForDisplay nameCountProvenance(AzquoCell azquoCell) {
        StringBuilder provString = new StringBuilder();
        Set<Name> cellNames = new HashSet<>();
        Name nameCountHeading = null;
        for (DataRegionHeading rowHeading : azquoCell.getRowHeadings()) {
            if (rowHeading != null) { // apparently it can be . . . is this a concern? Well NPE is no good, could error maggage on the else if this is a problem
                if (rowHeading.getFunction() == DataRegionHeading.FUNCTION.NAMECOUNT) {
                    provString.append("namecount(").append(rowHeading.getDescription());
                    nameCountHeading = rowHeading.getName();
                }
                if (rowHeading.getName() != null) {
                    cellNames.add(rowHeading.getName());
                }
            }
        }
        for (DataRegionHeading colHeading : azquoCell.getColumnHeadings()) {
            if (colHeading.getFunction() == DataRegionHeading.FUNCTION.NAMECOUNT) {
                provString.append("namecount(").append(colHeading.getDescription());
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
        return new ProvenanceDetailsForDisplay(provString.toString(), Collections.singletonList(provenanceForDisplay));
    }

    /* logic will be changed for new object ProvenanceDetailsForDisplay
     TODO - as mentioned, value history!
     */

    public static ProvenanceDetailsForDisplay getListOfChangedValues(AzquoMemoryDBConnection azquoMemoryDBConnection, int maxSize) throws Exception {
        return valuesProvenance(azquoMemoryDBConnection, azquoMemoryDBConnection.getValuesChanged(), maxSize);
    }


    private static ProvenanceDetailsForDisplay valuesProvenance(AzquoMemoryDBConnection azquoMemoryDBConnection, List<Value> values, int maxSize) throws Exception {
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
                    provenanceForDisplay.setValuesWithIdsAndNames(getIdValuesWithIdsAndNames(azquoMemoryDBConnection, oneUpdate));
                    checkAuditSheet(azquoMemoryDBConnection, provenanceForDisplay, oneUpdate);
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
            provenanceForDisplay.setValuesWithIdsAndNames(getIdValuesWithIdsAndNames(azquoMemoryDBConnection, oneUpdate));
            checkAuditSheet(azquoMemoryDBConnection, provenanceForDisplay, oneUpdate);
            provenanceForDisplays.add(provenanceForDisplay);
        }
        return new ProvenanceDetailsForDisplay(null, provenanceForDisplays);
    }

    private static final String AUDITSHEET = "AuditSheet";

/*    A normal in spreadsheet provenance might look as follows :
    {"user":"Bill Cawley","timeStamp":1495554373879,"method":"in spreadsheet","name":"Customer Categorisation","context":"month = May-16;"}
    An imported one might be
    {"user":"Bill Cawley","timeStamp":1495521349363,"method":"imported","name":"DG Setup2.xlsx:Mailouts","context":""}
    This is relevant as there's going to be an option to create a drill down equivalent for imports. The "Mailouts" bit above means that in that database
    there will be All import sheets -> DataImport Mailouts. The created drilldown will be an attribute AuditSheet against this name, a syntax is as follows :

    <Report Name> with CHOSENSET = <name of chosen set> CHOSEN FROM <choice set>, <Choice> CHOSEN FROM <choice set>,....

    e.g.  `Order Items chosen` with CHOSENSET = `Items chosen` CHOSEN FROM `All Order Items` children, Territory CHOSEN FROM `All countries` children, Month CHOSEN FROM `All months` children*/

    private static void checkAuditSheet(AzquoMemoryDBConnection azquoMemoryDBConnection, ProvenanceForDisplay provenanceForDisplay, List<Value> values) throws Exception {
        if ("imported".equals(provenanceForDisplay.getMethod())) {
            if (provenanceForDisplay.getName().contains(":")) {
                String toSearch = provenanceForDisplay.getName().substring(provenanceForDisplay.getName().lastIndexOf(":") + 1); // so we've got our Mailouts or equivalent
                Name allImportSheets = azquoMemoryDBConnection.getAzquoMemoryDBIndex().getNameByAttribute(Collections.singletonList(Constants.DEFAULT_DISPLAY_NAME), ValuesImport.ALLIMPORTSHEETS, null);
                // just check the children, more simple
                if (allImportSheets != null) {
                    for (Name child : allImportSheets.getChildren()) {
                        if (child.getDefaultDisplayName().equals("DataImport " + toSearch)) { // todo - stop such use of string literals . . .
                            if (child.getAttribute(AUDITSHEET) != null) { // then we have criteria
                                String auditSheetRule = child.getAttribute(AUDITSHEET);
                                if (auditSheetRule.contains("with")) { // this parsing could be more robust, also could be factored a bit?
                                    String reportName = auditSheetRule.substring(0, auditSheetRule.indexOf("with")).replaceAll("`", "").trim();
                                    provenanceForDisplay.setMethod("in spreadsheet");
                                    provenanceForDisplay.setInSpreadsheet(true);
                                    provenanceForDisplay.setName(reportName);
                                    // todo - parsing a bit more robust here regarding `, escaping names
                                    // dammit have to deal with character escapes . . .
                                    String restOfRule = auditSheetRule.substring(auditSheetRule.indexOf("with") + 4).trim();
                                    String CHOSENFROM = "CHOSEN FROM";
                                    if (restOfRule.toUpperCase().startsWith("CHOSENSET =")) {
                                        restOfRule = restOfRule.substring("CHOSENSET =".length()).trim();
                                        String chosenSet;
                                        String chosenSetChosenFrom; // new addition - we're not going to try to derive the chosen set from a set of
                                        // new logic - now chosen set also has a chosen from
                                        chosenSet = restOfRule.substring(0, restOfRule.indexOf(CHOSENFROM)).trim(); // not stripping quotes actually shouyld be fine for findOrCreateNameInParent
                                        restOfRule = restOfRule.substring(restOfRule.indexOf(CHOSENFROM) + CHOSENFROM.length());
/*                                        if (restOfRule.startsWith("`")){ // chosen set has escape quotes
                                            int endQuote = restOfRule.indexOf("`",1);
                                            chosenSet = restOfRule.substring(1, endQuote).trim();
                                            restOfRule = restOfRule.substring(endQuote + 1).trim();
                                        } else {
                                            int commaPos = restOfRule.indexOf(",");
                                            if (commaPos > 0){
                                                chosenSet = restOfRule.substring(0, commaPos).trim();
                                                restOfRule = restOfRule.substring(commaPos).trim();
                                            } else {
                                                chosenSet = restOfRule.trim();
                                                restOfRule = null; // no more there
                                            }
                                        }*/
                                        // so now get the chosen from criteria - might be able to factor this with code below - this was added after
                                        if (restOfRule.contains(CHOSENFROM)) {
                                            int chosenFromIndex = restOfRule.indexOf(CHOSENFROM);
                                            int commaPos = restOfRule.substring(0, chosenFromIndex).lastIndexOf(","); // required to stop picking up commas in quoted names
                                            chosenSetChosenFrom = restOfRule.substring(0, commaPos).trim();
                                            restOfRule = restOfRule.substring(commaPos).trim();
                                        } else { // it's just the rest
                                            chosenSetChosenFrom = restOfRule.trim();
                                            restOfRule = null; // no more there
                                        }
                                        Collection<Name> chosenSetChosenFromSet = NameQueryParser.parseQuery(azquoMemoryDBConnection, chosenSetChosenFrom);

                                        // ok, make the chosen set which are the names shared across all the values
                                        Set<Name> sharedSet = HashObjSets.newMutableSet();
                                        Set<Name> chosenSetSet = HashObjSets.newMutableSet(); // no longet spare based off multiple names, we look for names attacehd to the values in this set
                                        for (Value v : values) {
                                            if (sharedSet.isEmpty()) { // first one add them all
                                                sharedSet.addAll(v.getNames());
                                            } else {
                                                sharedSet.retainAll(v.getNames());
                                            }
                                            for (Name n : v.getNames()) {
                                                if (chosenSetChosenFromSet.contains(n)) {
                                                    chosenSetSet.add(n);
                                                }
                                            }
                                        }
                                        Name newSetForReport = NameService.findOrCreateNameInParent(azquoMemoryDBConnection, chosenSet, null, false);// simple name create on the new set
                                        newSetForReport.setChildrenWillBePersisted(chosenSetSet);

                                        StringBuilder context = new StringBuilder();
                                        if (restOfRule != null) {
                                            // we want to create a context along the lines of something = something; somethingelse = somethingelse;
                                            // parsing shouldn't be too difficult except that I need to watch out for commas in escaped names. If I parse around "CHOSEN FROM", assuming that isn't in the strings of course, this should be fairly robust
                                            // , Territory CHOSEN FROM `All countries` children, Month CHOSEN FROM `All months` children
                                            while (restOfRule.contains(CHOSENFROM)) {
                                                int chosenFromIndex = restOfRule.indexOf(CHOSENFROM);
                                                String choiceName = restOfRule.substring(0, chosenFromIndex);
                                                choiceName = choiceName.trim();
                                                if (choiceName.startsWith(",")) {
                                                    choiceName = choiceName.substring(1).trim();
                                                }
                                                String setToSelectFrom;
                                                restOfRule = restOfRule.substring(chosenFromIndex + CHOSENFROM.length());
                                                if (restOfRule.contains(CHOSENFROM)) {
                                                    int nextCommaPos = restOfRule.substring(0, restOfRule.indexOf(CHOSENFROM)).lastIndexOf(","); // the last comma before the next chosenFrom
                                                    setToSelectFrom = restOfRule.substring(0, nextCommaPos).trim();
                                                    restOfRule = restOfRule.substring(nextCommaPos);
                                                } else {
                                                    // no need to adjust restOfRule we're finished with it
                                                    setToSelectFrom = restOfRule.trim();
                                                }
                                                Collection<Name> setToSelectFromSet = NameQueryParser.parseQuery(azquoMemoryDBConnection, setToSelectFrom);
                                                // now, there should be ONE crossover between this set and the spare set
                                                setToSelectFromSet.retainAll(sharedSet);
                                                if (setToSelectFromSet.size() == 1) { // then we're in business!
                                                    context.append(choiceName + " = " + setToSelectFromSet.iterator().next().getDefaultDisplayName() + ";");
                                                }
                                            }
                                            provenanceForDisplay.setContext(context.toString());
                                        }
                                    }
                                }
                            }
                            break;
                        }
                    }
                }
            }
        }
    }


    // first string is the value, then the names . . .
    // needs the connection to check for historic values
    private static List<TypedPair<Integer, List<String>>> getIdValuesWithIdsAndNames(AzquoMemoryDBConnection azquoMemoryDBConnection, List<Value> values) {
        List<TypedPair<Integer, List<String>>> toReturn = new ArrayList<>();
        for (Value v : values) {
            List<Name> namesList = new ArrayList<>(v.getNames());
            namesList.sort(Comparator.comparingInt(Name::getValueCount));
            List<String> valueAndNames = new ArrayList<>();
            valueAndNames.add(v.getText());
            for (Name n : namesList) {
                valueAndNames.add(n.getDefaultDisplayName());
            }
            // now seartch for value history
            final List<ValueHistory> historyForValue = ValueDAO.getHistoryForValue(azquoMemoryDBConnection.getAzquoMemoryDB(), v);
            // now add them as strings to the end? SHould be ok
            if (!historyForValue.isEmpty()) {
                valueAndNames.add("Value History : ");
            }
            for (ValueHistory valueHistory : historyForValue) {
                valueAndNames.add(valueHistory.getText() + ", " + valueHistory.getProvenance().getProvenanceForDisplay().toString());
            }
            toReturn.add(new TypedPair<>(v.getId(), valueAndNames));
        }
        return toReturn;
    }


    private static DateTimeFormatter df = DateTimeFormatter.ofPattern("dd/MM/yy HH:mm");

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
        Map<Name, Integer> nameCounts = new HashMap<>();
        for (DummyValue value : values) {
            for (Name name : value.getNames()) {
                if (topParent == null || name.findATopParent() == topParent) {
                    Integer origCount = nameCounts.get(name);
                    if (origCount == null) {
                        nameCounts.put(name, 1);
                    } else {
                        nameCounts.put(name, origCount + 1);
                    }
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