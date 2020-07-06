package com.azquo.spreadsheet;

import com.azquo.*;
import com.azquo.memorydb.AzquoMemoryDBConnection;
import com.azquo.memorydb.core.Name;
import com.azquo.memorydb.core.Value;
import com.azquo.memorydb.service.*;
import com.azquo.spreadsheet.transport.RegionOptions;
import net.openhft.koloboke.collect.map.hash.HashIntDoubleMaps;
import net.openhft.koloboke.collect.map.hash.HashIntObjMaps;
import net.openhft.koloboke.collect.set.hash.HashObjSets;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracted from DSSpreadsheetService by edward on 28/10/16.
 * <p>
 * Core functions that create arrays of AzquoCells to be packaged by DSSpreadsheetService and sent to the front end.
 * <p>
 * A little over my 500 line limit but I'll leave it for the moment.
 */
public class AzquoCellService {

    private static final Runtime runtime = Runtime.getRuntime();

    static final int COL_HEADINGS_NAME_QUERY_LIMIT = 500;

    private static List<Integer> sortOnMultipleValues(Map<Integer, List<TypedPair<Double, String>>> sortListsMap, final boolean sortRowsUp) {
        int sortCount = sortListsMap.values().iterator().next().size();
        List<Boolean> doubleSort = new ArrayList<>();
        for (int i = 0; i < sortCount; i++) doubleSort.add(true);
        // ok I can't see a way around this, I'm going to have to check all doubles for a null and if I find one abandon the doublesort
        int doubleSorts = sortCount;
        for (List<TypedPair<Double, String>> check : sortListsMap.values()) {
            for (int i = 0; i < sortCount; i++) {
                TypedPair<Double, String> value = check.get(i);
                // hack for gaps, make them 0, lets see how it goes (of course one non blank will mean string sorting which is correct)
                if ("".equals(value.getSecond()) && value.getFirst() == null) {
                    check.set(i, new TypedPair<>(0.0, null));
                } else if (value.getFirst() == null) {
                    if (doubleSort.get(i)) {
                        doubleSort.set(i, false);
                        if (--doubleSorts == 0) {
                            break;
                        }
                    }
                }
            }
        }

        final List<Integer> sortedValues = new ArrayList<>(sortListsMap.size());
        List<Map.Entry<Integer, List<TypedPair<Double, String>>>> list = new ArrayList<>(sortListsMap.entrySet());
        // sort list based on the list of values in each entry
        try {
            list.sort((o1, o2) -> {
                int result = 0;
                if (o1.getValue().size() != o2.getValue().size()) { // the really should match! I'll call it neutral for the moment
                    return 0;
                }
                for (int index = 0; index < sortCount; index++) {
                    String v1 = o1.getValue().get(index).getSecond();
                    String v2 = o2.getValue().get(index).getSecond();
                    if (v2 == null && v1 != null) return -1;
                    if (v1 == null && v2 != null) return 1;
                    if (doubleSort.get(index)) {
                        result = o1.getValue().get(index).getFirst().compareTo(o2.getValue().get(index).getFirst());
                    } else {
                        result = o1.getValue().get(index).getSecond().compareTo(o2.getValue().get(index).getSecond());
                    }
                    if (result != 0) { // we found a difference
                        break;
                    }

                }
                return sortRowsUp ? result : -result;
            });
        } catch (Exception e) {
            //not sure what to do if there are null values in the list that needs to be sorted
        }
        for (Map.Entry<Integer, List<TypedPair<Double, String>>> entry : list) {
            for (int i = 0; i < sortCount; i++) {
                TypedPair<Double, String> value = entry.getValue().get(i);
            }
            sortedValues.add(entry.getKey());
        }
        return sortedValues;
    }

    private static List<Integer> sortDoubleValues(Map<Integer, Double> sortTotals, final boolean sortRowsUp) {
        final List<Integer> sortedValues = new ArrayList<>(sortTotals.size());
        List<Map.Entry<Integer, Double>> list = new ArrayList<>(sortTotals.entrySet());
        // sort list based on
        list.sort((o1, o2) -> sortRowsUp ? o1.getValue().compareTo(o2.getValue()) : -o1.getValue().compareTo(o2.getValue()));
        for (Map.Entry<Integer, Double> aList : list) {
            sortedValues.add(aList.getKey());
        }
        return sortedValues;
    }

    // filterTargetName use a where statement on the data based on values e.g. where linecount > 1, need to clarify the actya
    // now public as the NameQueryParser might want it
    public static List<List<AzquoCell>> getDataRegion(AzquoMemoryDBConnection azquoMemoryDBConnection, String regionName, List<List<String>> rowHeadingsSource
            , List<List<String>> colHeadingsSource, List<List<String>> contextSource, RegionOptions regionOptions, String user, int valueId, boolean quiet) throws Exception {
        if (!quiet) {
            azquoMemoryDBConnection.addToUserLog("Getting data for region : " + regionName);
        }
        List<String> languages = NameService.getDefaultLanguagesList(user);
        long track = System.currentTimeMillis();
        String calcExpression = null;

        long start = track;
        long threshold = 1000;
        // the context is changing to data region headings to support name function permutations - unlike the column and row headings it has to be flat, a resultant one dimensional list from createHeadingArraysFromSpreadsheetRegion
        final List<DataRegionHeading> contextHeadings = DataRegionHeadingService.getContextHeadings(azquoMemoryDBConnection, contextSource, languages);
        // look for context locked/unlokced suffix to apply to col and row headings that don't have suffixes
        DataRegionHeading.SUFFIX contextSuffix = null;
        for (DataRegionHeading contextHeading : contextHeadings) {
            if (contextHeading != null && (contextHeading.getSuffix() == DataRegionHeading.SUFFIX.LOCKED || contextHeading.getSuffix() == DataRegionHeading.SUFFIX.UNLOCKED)) {
                contextSuffix = contextHeading.getSuffix();
            }
        }

        long time = (System.currentTimeMillis() - track);
        if (time > threshold) System.out.println("Context parsed in " + time + "ms");
        track = System.currentTimeMillis();
        List<String> defaultLanguages = languages;
        if (regionOptions.rowLanguage != null && regionOptions.rowLanguage.length() > 0) {
            languages = new ArrayList<>();
            languages.add(regionOptions.rowLanguage);
        }
        final List<List<List<DataRegionHeading>>> rowHeadingLists = DataRegionHeadingService.createHeadingArraysFromSpreadsheetRegion(azquoMemoryDBConnection, rowHeadingsSource, languages, contextSuffix, regionOptions.ignoreHeadingErrors);
        languages = defaultLanguages;
        time = (System.currentTimeMillis() - track);
        if (time > threshold) System.out.println("Row headings parsed in " + time + "ms");
        track = System.currentTimeMillis();
        Collection<Name> sharedNames = getSharedNames(contextHeadings);//sharedNames only required for permutations
        final List<List<DataRegionHeading>> rowHeadings = DataRegionHeadingService.expandHeadings(rowHeadingLists, sharedNames, regionOptions.noPermuteTotals);
        time = (System.currentTimeMillis() - track);
        if (time > threshold) System.out.println("Row headings expanded in " + time + "ms");
        track = System.currentTimeMillis();
        if (regionOptions.columnLanguage != null && regionOptions.columnLanguage.length() > 0) {
            languages = new ArrayList<>();
            languages.add(regionOptions.columnLanguage);
        }
             String firstColumnHeading = colHeadingsSource.get(0).get(0);
             Pattern p = Pattern.compile("" + StringLiterals.QUOTE + "[^" + StringLiterals.QUOTE + "]*" + StringLiterals.QUOTE + " ="); //`name`=
            Matcher matcher = p.matcher(firstColumnHeading);
            if (matcher.find()) {
                calcExpression = firstColumnHeading.substring(matcher.end()).trim();
                calcExpression = extractConstantsFromCalcExpression(azquoMemoryDBConnection, calcExpression, contextHeadings);
                String target = matcher.group();
                target = target.substring(0, target.length() - 2);//remove ' ='
                colHeadingsSource.get(0).set(0, target);
            }
        final List<List<List<DataRegionHeading>>> columnHeadingLists = DataRegionHeadingService.createHeadingArraysFromSpreadsheetRegion(azquoMemoryDBConnection, colHeadingsSource, languages, AzquoCellService.COL_HEADINGS_NAME_QUERY_LIMIT, contextSuffix, regionOptions.ignoreHeadingErrors);
        languages = defaultLanguages;
        time = (System.currentTimeMillis() - track);
        if (time > threshold) System.out.println("Column headings parsed in " + time + "ms");
        track = System.currentTimeMillis();
        final List<List<DataRegionHeading>> columnHeadings = DataRegionHeadingService.expandHeadings(MultidimensionalListUtils.transpose2DList(columnHeadingLists), sharedNames, regionOptions.noPermuteTotals);
        if (calcExpression!=null) {
            //add another column for the calcExpression
            List<DataRegionHeading> calcItem = new ArrayList<>();
            calcItem.add(new DataRegionHeading(null, false, null, null, null, null, null, 0, calcExpression));
            columnHeadings.add(calcItem);
        }
        time = (System.currentTimeMillis() - track);
        if (time > threshold) System.out.println("Column headings expanded in " + time + "ms");
        track = System.currentTimeMillis();
        if (columnHeadings.size() == 0) {
            throw new Exception("no headings passed");
        }
        if (rowHeadings.size() == 0) {
            //create a single row heading
            rowHeadings.add(new ArrayList<>());
            rowHeadings.get(0).add(new DataRegionHeading(null, false, null));
        }
        List<List<AzquoCell>> dataToShow = getAzquoCellsForRowsColumnsAndContext(azquoMemoryDBConnection, rowHeadings, columnHeadings, contextHeadings, languages, valueId, quiet);
        time = (System.currentTimeMillis() - track);
        if (time > threshold) System.out.println("data populated in " + time + "ms");
        if (time > 5000) { // a bit arbitrary
            ValueService.printFindForNamesIncludeChildrenStats();
            ValueCalculationService.printSumStats();
        }
        track = System.currentTimeMillis();
        boolean permute = false;
        if (rowHeadingsSource.size() == 1 && rowHeadingsSource.get(0).get(0).toLowerCase().startsWith("permute("))
            permute = true;
        dataToShow = sortAndFilterCells(dataToShow, rowHeadings
                , regionOptions, permute);
        time = (System.currentTimeMillis() - track);
        if (time > threshold) System.out.println("data sort/filter in " + time + "ms");
        System.out.println("region delivered in " + (System.currentTimeMillis() - start) + "ms");
        return dataToShow;
    }

    // used by the pivot permute function, really it's building a set of shared names based on all the children of names specified in context


    private static String extractConstantsFromCalcExpression(AzquoMemoryDBConnection azquoMemoryDBConnection, String expression, List<DataRegionHeading> context) throws  Exception{
        String error = expression + " not understood";
        String[] elements = expression.split("\\|");
        if (elements.length==1) return expression;
        String toReturn = "";
        List<String> constant = new ArrayList<>();
        for (String element:elements) {
            while (element.length() > 0) {
                int quotePos = element.lastIndexOf(StringLiterals.QUOTE + "");
                if (quotePos < 0) {
                    throw new Exception(error);
                }
                quotePos = element.lastIndexOf(StringLiterals.QUOTE + "", quotePos - 1);
                if (quotePos < 0) {
                    throw new Exception(error);
                }
                if (quotePos > 0) {
                    if (constant.size() != 0){
                        toReturn += resolveConstant(azquoMemoryDBConnection, constant, context);
                    }
                    toReturn += element.substring(0, quotePos);
                    element = element.substring(quotePos);
                }
                if (!element.startsWith(StringLiterals.QUOTE + "") || element.length() < 3) {
                    throw new Exception(error);
                }
                quotePos = element.indexOf(StringLiterals.QUOTE + "",1);
                if (quotePos < 0) {
                    throw new Exception(error);
                }
                constant.add(element.substring(0, ++quotePos));
                element = element.substring(quotePos);
            }
        }
        if (constant.size() > 0){
            toReturn += resolveConstant(azquoMemoryDBConnection, constant, context);
        }
        return toReturn;

    }

    private static String resolveConstant(AzquoMemoryDBConnection azquoMemoryDBConnection, List<String> constant, List<DataRegionHeading>context)throws Exception{
         List<Name> cellNames = new ArrayList<>();
        for (String nameString:constant){
            cellNames.add(NameService.findByName(azquoMemoryDBConnection,nameString));

        }
        cellNames.addAll(DataRegionHeadingService.namesFromDataRegionHeadings(context));
        AzquoCellResolver.ValuesHook valuesHook = new AzquoCellResolver.ValuesHook();
        MutableBoolean locked = new MutableBoolean();
        double d = ValueService.findValueForNames(azquoMemoryDBConnection,cellNames,null,locked,valuesHook,null,null, null,null,null,null,null);
        if (d !=0){
            return d + "";
        }
        if (valuesHook.values.size() ==1){
            return valuesHook.values.get(0).getText();
        }
        return "";
    }

    static Collection<Name> getSharedNames(List<DataRegionHeading> headingList) {
        //long startTime = System.currentTimeMillis();
        Collection<Name> shared = null;
        List<Collection<Name>> relevantNameSets = new ArrayList<>();
        // gather names
        for (DataRegionHeading heading : headingList) {
            if (heading.getName() == null || (heading.getName().getAttribute("CALCULATION")== null && (heading.getName().getChildren().size() == 0 && heading.getName().getValues().size() == 0))) {
                return new ArrayList<>();
            }
            if (heading.getName() != null && heading.getName().getChildren().size() > 0) {
                relevantNameSets.add(heading.getName().findAllChildren());
            }
        }
        // then similar logic to getting the values for names
        if (relevantNameSets.size() == 1) {
            shared = relevantNameSets.get(0);
        } else if (relevantNameSets.size() > 1) {
            shared = HashObjSets.newMutableSet();// I need a set it will could be hammered with contains later
            // similar logic to getting values for a name. todo - can we factor?
            Collection<Name> smallestNameSet = null;
            for (Collection<Name> nameSet : relevantNameSets) {
                if (smallestNameSet == null || nameSet.size() < smallestNameSet.size()) {
                    // note - in other similar logic there was a check to exit immediately in the event of an empty set but this can't happen here as we ignore childless names (should we??)
                    smallestNameSet = nameSet;
                }
            }
            assert smallestNameSet != null; // make intellij happy
            Collection[] setsToCheck = new Collection[relevantNameSets.size() - 1];
            int arrayIndex = 0;
            for (Collection<Name> nameSet : relevantNameSets) {
                if (nameSet != smallestNameSet) {
                    setsToCheck[arrayIndex] = nameSet;
                    arrayIndex++;
                }
            }
            boolean add; // declare out here, save reinitialising each time
            int index; // ditto that, should help
            for (Name name : smallestNameSet) {
                add = true;
                for (index = 0; index < setsToCheck.length; index++) {
                    if (!setsToCheck[index].contains(name)) {
                        add = false;
                        break;
                    }
                }
                if (add) {
                    shared.add(name);
                }
            }
        }
//        int size = 0;
//        if (shared != null) size = shared.size();
        //System.out.println("time to get shared names " + (System.currentTimeMillis() - startTime) + " count = " + size);
        return shared;
    }

    // have removed non number support

    private static int findPosition(String toFind) {
        if (toFind == null || toFind.length() == 0) {
            return -1;
        }
        try {
            int col = Integer.parseInt(toFind.trim());
            if (col > 0) {
                return col - 1;
            }
        } catch (Exception e) {
            //so it isn't a number!
        }
/*        toFind = toFind.replace(" ", "").replace("`", "").toLowerCase();
        int count = 0;
        for (List<DataRegionHeading> heading : headings) {
            DataRegionHeading dataRegionHeading = heading.get(heading.size() - 1);
            if (dataRegionHeading != null) {
                if (dataRegionHeading.getName() != null) {
                    // ok now we need to deal with the languages! This function didn't before
                    for (String language : languages) {
                        String languageValue = dataRegionHeading.getName().getAttribute(language);
                        // just run through the relevant languages looking for this column
                        if (languageValue != null && languageValue.replace(" ", "").replace("`", "").toLowerCase().equals(toFind)) {
                            return count;
                        }
                    }
                } else {
                    if (dataRegionHeading.getAttribute().replace(" ", "").replace("`", "").toLowerCase().equals(toFind)) {
                        return count;
                    }
                }
            }
            count++;
        }*/
        return -1;
    }

    // note, one could derive column and row headings from the source data's headings but passing them is easier if they are to hand which the should be
    // also deals with highlighting
    // I've tried to make parameter names useful but they can be a bit confusing as we have for example a column index to sort on but the values will be row values or totals for that row

    private static List<List<AzquoCell>> sortAndFilterCells(List<List<AzquoCell>> sourceData, List<List<DataRegionHeading>> rowHeadings
            , RegionOptions regionOptions, boolean permute) {
        final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("dd-MMM-yy");
        List<List<AzquoCell>> toReturn = sourceData;
        if (sourceData == null || sourceData.isEmpty()) {
            return sourceData;
        }
        // need to be able to sort on multiple columns now - find the indexes here. Order is important I assume!
        List<Integer> sortOnColIndexes = new ArrayList<>();
        if (regionOptions.sortColumn != null) {
            for (String sc : regionOptions.sortColumn.split("&")) {
                int sortColIndex = findPosition(sc);
                if (sortColIndex != -1) {
                    sortOnColIndexes.add(sortColIndex);
                }
            }
        }
        int sortOnRowIndex = findPosition(regionOptions.sortRow); // not used much I don't think but we'll allow a simple numeric left/right sort, possibly on totals
        // sorting on row or col totals only happens if a sort row or col hasn't been passed and there are more rows or cols than max rows or cols
        int totalRows = sourceData.size();
        int totalCols = sourceData.get(0).size();

        boolean sortOnColTotals = false;
        boolean sortOnRowTotals = false;
        int maxRows = regionOptions.rowLimit;
        boolean sortColAsc = regionOptions.sortColumnAsc;
        // bit of a hack - max rows can be negative, whether negative or positive changes sort order in the case of totals
        if (maxRows != 0) {
            if (Math.abs(maxRows) < totalRows) {
                if (sortOnColIndexes.isEmpty()) { // only cause total sorting if column(s) to sort on have not been specified
                    sortOnRowTotals = true;
                    sortColAsc = maxRows < 0;
                }
            } else {
                maxRows = 0; // zero it as it's a moot point
            }
        }
        int maxCols = regionOptions.columnLimit;
        if (maxCols != 0) {
            if (Math.abs(maxCols) < totalCols) {
                if (sortOnRowIndex == -1) { // only cause total sorting if a sorting row hasn't been passed
                    sortOnColTotals = true;
                    sortColAsc = maxCols < 0;
                }
            } else {
                maxCols = 0;
            }
        }
        maxRows = Math.abs(maxRows);
        maxCols = Math.abs(maxCols);
        if (!sortOnColIndexes.isEmpty() || sortOnRowIndex != -1 || sortOnColTotals || sortOnRowTotals) { // then there's sorting to do
            // for up/down sorting we now support mor than one column, it might bee a mix of strings and doubles, hence lists of typed pairs
            final Map<Integer, List<TypedPair<Double, String>>> sortRowValuesMap = HashIntObjMaps.newMutableMap(sourceData.size());
            // for left/right sorting, only supports numbers and one row to sort on or the column totals
            final Map<Integer, Double> sortColumnDoubles = HashIntDoubleMaps.newMutableMap(totalCols);
            // blank the map to stop npes later
            for (int colNo = 0; colNo < totalCols; colNo++) {
                sortColumnDoubles.put(colNo, 0.00);
            }
            int rowNo = 0;
            // populate the maps that will be sorted
            for (List<AzquoCell> rowCells : sourceData) {
                List<TypedPair<Double, String>> sortRowValues = new ArrayList<>();// what, in order, are the relevant sort values for this row? Can be numbers of strings . .
                double sortRowTotal = 0.0;
                int colNo = 0;
                // initial pass through the row
                for (AzquoCell cell : rowCells) {
                    // info for an up/down row total sort
                    if (sortOnRowTotals && !DataRegionHeadingService.headingsHaveAttributes(cell.getColumnHeadings())) { // gather the row total if required
                        sortRowTotal += cell.getDoubleValue();
                    }
                    // info for a left/rigt number sort either on column totals or a specific row
                    if ((sortOnColTotals && !DataRegionHeadingService.headingsHaveAttributes(cell.getRowHeadings())) || sortOnRowIndex == rowNo) {
                        sortColumnDoubles.put(colNo, sortColumnDoubles.get(colNo) + cell.getDoubleValue());
                    }
                    colNo++;
                }
                if (sortOnRowTotals) { // a simple sort on one number
                    sortRowValues.add(new TypedPair<>(sortRowTotal, null));
                } else { // ok here we go, need to support multiple sort columns
                    // try to make number sorting work where possible by saying that blank is 0
                    for (Integer index : sortOnColIndexes) {
                        AzquoCell cell = null;
                        if (index < rowCells.size()) {
                            cell = rowCells.get(index);
                        }
                        if (cell == null) { // blank, be flexible to the sorting later
                            sortRowValues.add(new TypedPair<>(0d, ""));
                        } else {
                            Double d = null;
                            try {
                                d = Double.parseDouble(cell.getStringValue());
                            } catch (Exception e) {
                                try {
                                    d = Double.parseDouble(cell.getStringValue().replace(":", "."));//to cater for times (e.g. 9:00)
                                } catch (Exception e2) {
                                    //not a number - ignore
                                }
                            }
                            String stringVal = cell.getStringValue();
                            try {
                                LocalDate date = LocalDate.parse("01-" + stringVal, dateTimeFormatter);
                                d = (double) date.toEpochDay();

                            } catch (Exception e) {
                                //ignore.  If every val produces a double, then the double will be sorted.
                            }

                            sortRowValues.add(new TypedPair<>(d, cell.getStringValue()));
                        }
                    }
                }
                sortRowValuesMap.put(rowNo, sortRowValues);
                rowNo++;
            }
            List<Integer> sortedRows = new ArrayList<>();
            if (!sortOnColIndexes.isEmpty() || sortOnRowTotals) { // then we need to sort the rows
                if (permute&& !regionOptions.noPermuteTotals) {
                    // ok we need this to work on multiple levels. Start on the right and sort everything except totals
                    boolean first = true;
                    for (int sortingHeadingIndex = rowHeadings.get(0).size() - 1; sortingHeadingIndex >= 0; sortingHeadingIndex--){
                        if (first){
                            // the right column, quite simple - batch up each little section in subTotalSortRowValues,
                            // when you hit a total (permute) sort the lines and jam them and the total into sorted rows
                            Map<Integer, List<TypedPair<Double, String>>> subTotalSortRowValues = new HashMap<>();
                            for (int row = 0; row < sortRowValuesMap.size(); row++) {
                                if (rowHeadings.get(row).get(sortingHeadingIndex).getFunction() == DataRegionHeading.FUNCTION.PERMUTE) { // means it's a total line
                                    if (!subTotalSortRowValues.isEmpty()){
                                        sortedRows.addAll(sortOnMultipleValues(subTotalSortRowValues, sortColAsc));
                                        subTotalSortRowValues.clear();
                                    }
                                    sortedRows.add(row); // totals currently not touched - jam them in as they were
                                } else {
                                    subTotalSortRowValues.put(row, sortRowValuesMap.get(row));
                                }
                            }
                            first = false;
                        } else {
                            // sort totals, need to move things around in chunks, a little more complex
                            // stores the chunks of rows associated with each total that we're sorting, the key for this map and the next is the row the total is on
                            Map<Integer, List<Integer>> rowsForTotal = new HashMap<>();
                            // the totals themselves that we want to sort
                            Map<Integer, List<TypedPair<Double, String>>> totalsToSort = new HashMap<>();
                            List<Integer> newSortedRows = new ArrayList<>();
                            // lastId and thisId are simply used to tell when the name at this level changes
                            int lastId = rowHeadings.get(sortedRows.get(0)).get(sortingHeadingIndex).getName().getId();
                            ArrayList<Integer> currentChunk = new ArrayList<>();
                            // by this point there will have been sorting so need to go through like this rather than a straight for loop as above
                            for (Integer row : sortedRows) {
                                int thisId = rowHeadings.get(row).get(sortingHeadingIndex).getName().getId();
                                if (thisId != lastId){ // end of the current chunk
                                    if (!currentChunk.isEmpty()){// it might be empty if there were higher level totals
                                        int totalRowIndex = currentChunk.get(currentChunk.size() - 1); // total is the one before this row, the last in that chunk
                                        // so put the chunk and total in the maps and clear the chunk
                                        totalsToSort.put(totalRowIndex,sortRowValuesMap.get(totalRowIndex));
                                        rowsForTotal.put(totalRowIndex, currentChunk);
                                        currentChunk = new ArrayList<>();
                                    }
                                    // not only the end of the chunk but the end of the set of chunks we want to sort
                                    if (rowHeadings.get(row).get(sortingHeadingIndex).getFunction() == DataRegionHeading.FUNCTION.PERMUTE){
                                        if (!totalsToSort.isEmpty()){
                                            // sort the totals
                                            List<Integer> totalIndexesInOrder = sortOnMultipleValues(totalsToSort, sortColAsc);
                                            for (Integer totalIndex : totalIndexesInOrder){
                                                newSortedRows.addAll(rowsForTotal.get(totalIndex));
                                            }
                                            totalsToSort.clear();
                                            rowsForTotal.clear();
                                        }
                                        newSortedRows.add(row); // the permute lines stay where they are - we're sorting the stuff between tham
                                    } else { // new chunk
                                        currentChunk.add(row);
                                    }
                                    lastId = thisId;
                                } else {
                                    currentChunk.add(row);
                                }
                            }
                            newSortedRows.addAll(currentChunk); // any leftovers, totals probably
                            sortedRows = newSortedRows;
                        }
                    }
                } else {
                    sortedRows = sortOnMultipleValues(sortRowValuesMap, sortColAsc);
                }
            }

            List<Integer> sortedCols = null;
            if (sortOnRowIndex != -1 || sortOnColTotals) { // then we need to sort the cols
                sortedCols = sortDoubleValues(sortColumnDoubles, regionOptions.sortRowAsc);
            }
            // OK pasting and changing what was in format data region, it's only called by this
            // zero passed or set above means don't limit, this feels a little hacky but we need a less than condition on the for loop. Both for limiting and we need this type of loop as the index looks up on the sort
            if (maxRows == 0) {
                maxRows = totalRows;
            }
            if (maxCols == 0) {
                maxCols = totalCols;
            }
            List<List<AzquoCell>> sortedCells = new ArrayList<>(maxRows); // could be less due to blanked cells but I don't see a huge harm
            for (rowNo = 0; rowNo < maxRows; rowNo++) {
                List<AzquoCell> rowCells = sourceData.get(sortedRows.size() > 0 ? sortedRows.get(rowNo) : rowNo); // if a sort happened use the row number according to it
                List<AzquoCell> newRow;
                if (sortedCols != null) {
                    newRow = new ArrayList<>(maxCols);
                    for (int colNo = 0; colNo < maxCols; colNo++) {
                        newRow.add(rowCells.get(sortedCols.get(colNo)));
                    }
                } else { // just jam in the row unchanged
                    newRow = rowCells;
                }
                sortedCells.add(newRow);
            }
            toReturn = sortedCells;
        }
        if (regionOptions.hideRows > 0) {
            int rowNo = 0;
            while (rowNo < toReturn.size()) {
                boolean rowsBlank = true;
                for (int j = 0; j < regionOptions.hideRows; j++) {
                    List<AzquoCell> rowToCheck = toReturn.get(rowNo + j); // size - 1 for the last index
                    for (AzquoCell cellToCheck : rowToCheck) {
                        if ((regionOptions.hideRowValues == 0 || !hasAttribute(cellToCheck)) && cellToCheck.getStringValue() != null && cellToCheck.getStringValue().length() > 0 && !cellToCheck.getStringValue().equals("0.0") && !cellToCheck.getStringValue().equals("0")) { // EFC note : not quite sure how plain 0 gets in there but it should be hidden also
                            rowsBlank = false;
                            break;
                        }
                    }
                    if (!rowsBlank) {
                        break;
                    }
                }
                if (rowsBlank) {
                    for (int i = 0; i < regionOptions.hideRows; i++) {
                        toReturn.remove(rowNo);
                    }
                } else {
                    rowNo += regionOptions.hideRows;
                }
            }
        }
        if (regionOptions.hideCols > 0) {
            int colNo = 0;
            while (colNo < toReturn.get(0).size()) {
                boolean colsBlank = true;
                for (int j = 0; j < regionOptions.hideCols; j++) {
                    for (List<AzquoCell> azquoCells : toReturn) {
                        AzquoCell cellToCheck = azquoCells.get(colNo + j);
                        if (cellToCheck.getStringValue() != null && cellToCheck.getStringValue().length() > 0 && !cellToCheck.getStringValue().equals("0.0")) {
                            colsBlank = false;
                            break;
                        }
                    }
                    if (!colsBlank) {
                        break;
                    }
                }
                if (colsBlank) {
                    for (int i = 0; i < regionOptions.hideCols; i++) {
                        for (List<AzquoCell> azquoCells : toReturn) {
                            azquoCells.remove(colNo);
                        }
                    }
                } else {
                    colNo += regionOptions.hideCols;
                }
            }
        }
        // the hide cols could make empty rows, this trips up code. Zap the empty rows
        toReturn.removeIf(List::isEmpty);

        // it's at this point we actually have data that's going to be sent to a user in newRow so do the highlighting here I think
        if (regionOptions.highlightDays > 0) {
            int highlightHours = regionOptions.highlightDays * 24;
            //hack to allow highlight one hour
            if (regionOptions.highlightDays == 2) highlightHours = 1; // todo - get rid of this!
            for (List<AzquoCell> row : toReturn) {
                for (AzquoCell azquoCell : row) {
                    long age = 1000000; // about 30 years old as default
                    ListOfValuesOrNamesAndAttributeName valuesForCell = azquoCell.getListOfValuesOrNamesAndAttributeName();
                    if (valuesForCell != null && (valuesForCell.getValues() != null && !valuesForCell.getValues().isEmpty())) {
                        for (Value value : valuesForCell.getValues()) {
                            if (value.getText().length() > 0) {
                                if (value.getProvenance() == null) {
                                    System.out.println("provenance null for " + value); // bit of a hack but lets log it
                                    //break;
                                } else {
                                    LocalDateTime provdate = value.getProvenance().getTimeStamp();
                                    long cellAge = provdate.until(LocalDateTime.now(), ChronoUnit.HOURS);
                                    if (cellAge < age) {
                                        age = cellAge;
                                    }
                                }
                            }
                        }
                    }
                    if (highlightHours > age) {
                        azquoCell.setAsHighlighted();
                    }
                }
            }
        }
        return toReturn;
    }


    private static boolean hasAttribute(AzquoCell cell) {
        for (DataRegionHeading heading : cell.getColumnHeadings()) {

            if (heading != null && heading.getAttribute() != null && !heading.getAttribute().equals(".")) return true;
        }
        for (DataRegionHeading heading : cell.getRowHeadings()) {
            if (heading != null && heading.getAttribute() != null && !heading.getAttribute().equals(".")) return true;

        }
        return false;

    }


    public static List<List<AzquoCell>> getAzquoCellsForRowsColumnsAndContext(AzquoMemoryDBConnection connection, List<List<DataRegionHeading>> headingsForEachRow
            , final List<List<DataRegionHeading>> headingsForEachColumn
            , final List<DataRegionHeading> contextHeadings, List<String> languages, int valueId, boolean quiet) throws Exception {
        long oldHeapMarker = (runtime.totalMemory() - runtime.freeMemory());
        long newHeapMarker;
        long track = System.currentTimeMillis();
        int totalRows = headingsForEachRow.size();
        int totalCols = headingsForEachColumn.size();
/*        for (int i = 0; i < totalRows; i++) {
            toReturn.add(null);// null the rows, basically adding spaces to the return list
        }*/
        List<List<AzquoCell>> toReturn = new ArrayList<>(totalRows);
        if (!quiet) {
            // todo - depending on filter the size might not be accurate which is a red herring at least for client side processing
            connection.addToUserLog("Size = " + totalRows + " * " + totalCols);
            connection.addToUserLog("1%--------25%---------50%---------75%--------100%");
        }
        int maxRegionSize = 5000000;//EFC 1/2/19
        if (totalRows * totalCols > maxRegionSize) {
            throw new Exception("Data region too large - " + totalRows + " * " + totalCols + ", max cells " + maxRegionSize);
        }
        ExecutorService executor = ThreadPools.getMainThreadPool();
        int progressBarStep = (totalCols * totalRows) / 50 + 1;
        AtomicInteger counter = new AtomicInteger();
        Map<List<Name>, Set<Value>> nameComboValueCache = null;
        if (totalRows > 100) {
            nameComboValueCache = new ConcurrentHashMap<>(); // then try the cache which should speed up heading combinations where there's a lot of overlap
        }
        // I was passing an ArrayList through to the tasks. This did seem to work but as I understand a Callable is what's required here, takes care of memory sync and exceptions
        if (totalRows < 1000) { // arbitrary cut off for the moment, Future per cell. I wonder if it should be lower than 1000?
            List<List<Future<AzquoCell>>> futureCellArray = new ArrayList<>();
            for (int row = 0; row < totalRows; row++) {
                List<DataRegionHeading> rowHeadings = headingsForEachRow.get(row);
                DataRegionHeading lastHeading = rowHeadings.get(rowHeadings.size() - 1);
                if (AzquoCellResolver.isDot(lastHeading)) {
                    List<DataRegionHeading> newRowHeadings = new ArrayList<>();
                    for (int headingNo = 0; headingNo < rowHeadings.size() - 1; headingNo++) {
                        newRowHeadings.add(null);
                    }
                    newRowHeadings.add(lastHeading);
                    rowHeadings = newRowHeadings;
                }
                //a '.' on the final heading means a blank line
                List<Future<AzquoCell>> futureRow = new ArrayList<>(headingsForEachColumn.size());
                /*for (int i = 0; i < headingsForEachColumn.size(); i++) {
                    returnRow.add(null);// yes a bit hacky, want to make the space that will be used by cellfiller
                }*/
                int colNo = 0;

                for (List<DataRegionHeading> columnHeadings : headingsForEachColumn) {
                    DataRegionHeading lastColHeading = rowHeadings.get(rowHeadings.size() - 1);
                    if (AzquoCellResolver.isDot(lastColHeading)) {
                        List<DataRegionHeading> newColumnHeadings = new ArrayList<>();
                        for (int headingNo = 0; headingNo < columnHeadings.size() - 1; headingNo++) {
                            newColumnHeadings.add(null);
                        }
                        newColumnHeadings.add(lastColHeading);
                        columnHeadings = newColumnHeadings;
                    }
                    // inconsistent parameter ordering?
                    futureRow.add(executor.submit(new CellFiller(row, colNo, columnHeadings, rowHeadings, contextHeadings, languages, valueId, connection, counter, progressBarStep, nameComboValueCache, quiet)));
                    colNo++;
                }
                futureCellArray.add(futureRow);
                //toReturn.set(row, returnRow);
            }
            // so the future cells have been racked up, let's try and get them
            for (List<Future<AzquoCell>> futureRow : futureCellArray) {
                List<AzquoCell> row = new ArrayList<>();
                for (Future<AzquoCell> futureCell : futureRow) {
                    row.add(futureCell.get());
                }
                toReturn.add(row);
            }
        } else { // Future per row
            List<Future<List<AzquoCell>>> futureRowArray = new ArrayList<>();
            for (int row = 0; row < totalRows; row++) {
                // row passed twice as
                futureRowArray.add(executor.submit(new RowFiller(row, headingsForEachColumn, headingsForEachRow, contextHeadings, languages, valueId, connection, counter, progressBarStep, quiet)));
            }
            for (Future<List<AzquoCell>> futureRow : futureRowArray) {
                toReturn.add(futureRow.get(1, TimeUnit.HOURS));
            }
        }
        newHeapMarker = (runtime.totalMemory() - runtime.freeMemory());
        System.out.println();
        int mb = 1024 * 1024;
        System.out.println("Heap cost to make on multi thread : " + (newHeapMarker - oldHeapMarker) / mb);
        System.out.println();
        //oldHeapMarker = newHeapMarker;
        if (!quiet) {
            connection.addToUserLog(" time : " + (System.currentTimeMillis() - track) + "ms");
        }
        return toReturn;
    }

 }

