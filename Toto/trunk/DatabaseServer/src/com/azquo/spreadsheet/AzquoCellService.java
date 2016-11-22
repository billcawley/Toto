package com.azquo.spreadsheet;

import com.azquo.ThreadPools;
import com.azquo.memorydb.AzquoMemoryDBConnection;
import com.azquo.memorydb.core.Name;
import com.azquo.memorydb.core.Value;
import com.azquo.memorydb.service.ValueCalculationService;
import com.azquo.memorydb.service.ValueService;
import com.azquo.spreadsheet.view.RegionOptions;
import net.openhft.koloboke.collect.map.hash.HashIntDoubleMaps;
import net.openhft.koloboke.collect.map.hash.HashIntObjMaps;
import net.openhft.koloboke.collect.set.hash.HashObjSets;
import org.apache.commons.lang.math.NumberUtils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Extracted from DSSpreadsheetService by edward on 28/10/16.
 *
 * Core functions that create arrays of AzquoCells to be packaged by DSSpreadsheetService and sent to the front end.
 *
 * A little over my 500 line limit but I'll leave it for the moment.
 */
class AzquoCellService {

    private static final Runtime runtime = Runtime.getRuntime();

    static final int COL_HEADINGS_NAME_QUERY_LIMIT = 500;

    private static List<Integer> sortDoubleValues(Map<Integer, Double> sortTotals, final boolean sortRowsUp) {
        final List<Integer> sortedValues = new ArrayList<>(sortTotals.size());
        List<Map.Entry<Integer, Double>> list = new ArrayList<>(sortTotals.entrySet());
        // sort list based on
        Collections.sort(list, (o1, o2) -> sortRowsUp ? o1.getValue().compareTo(o2.getValue()) : -o1.getValue().compareTo(o2.getValue()));
        for (Map.Entry<Integer, Double> aList : list) {
            sortedValues.add(aList.getKey());
        }
        return sortedValues;
    }

    // same thing for strings, I prefer stronger typing
    private static List<Integer> sortStringValues(Map<Integer, String> sortTotals, final boolean sortRowsUp) {
        final List<Integer> sortedValues = new ArrayList<>(sortTotals.size());
        List<Map.Entry<Integer, String>> list = new ArrayList<>(sortTotals.entrySet());
        // sort list based on string now
        Collections.sort(list, (o1, o2) -> sortRowsUp ? o1.getValue().compareTo(o2.getValue()) : -o1.getValue().compareTo(o2.getValue()));

        for (Map.Entry<Integer, String> aList : list) {
            sortedValues.add(aList.getKey());
        }
        return sortedValues;
    }

    static List<List<AzquoCell>> getDataRegion(AzquoMemoryDBConnection azquoMemoryDBCOnnection, String regionName, List<List<String>> rowHeadingsSource
            , List<List<String>> colHeadingsSource, List<List<String>> contextSource, RegionOptions regionOptions, List<String> languages, int valueId, boolean quiet) throws Exception {
        if (!quiet){
            azquoMemoryDBCOnnection.addToUserLog("Getting data for region : " + regionName);
        }
        long track = System.currentTimeMillis();
        long start = track;
        long threshold = 1000;
        // the context is changing to data region headings to support name function permutations - unlike the column and row headings it has to be flat, a resultant one dimensional list from createHeadingArraysFromSpreadsheetRegion
        final List<DataRegionHeading> contextHeadings = DataRegionHeadingService.getContextHeadings(azquoMemoryDBCOnnection, contextSource, languages);
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
        final List<List<List<DataRegionHeading>>> rowHeadingLists = DataRegionHeadingService.createHeadingArraysFromSpreadsheetRegion(azquoMemoryDBCOnnection, rowHeadingsSource, languages, contextSuffix, regionOptions.ignoreHeadingErrors);
        time = (System.currentTimeMillis() - track);
        if (time > threshold) System.out.println("Row headings parsed in " + time + "ms");
        track = System.currentTimeMillis();
        Collection<Name> sharedNames = getSharedNames(contextHeadings);//sharedNames only required for permutations
        final List<List<DataRegionHeading>> rowHeadings = DataRegionHeadingService.expandHeadings(rowHeadingLists, sharedNames);
        time = (System.currentTimeMillis() - track);
        if (time > threshold) System.out.println("Row headings expanded in " + time + "ms");
        track = System.currentTimeMillis();
        final List<List<List<DataRegionHeading>>> columnHeadingLists = DataRegionHeadingService.createHeadingArraysFromSpreadsheetRegion(azquoMemoryDBCOnnection, colHeadingsSource, languages, AzquoCellService.COL_HEADINGS_NAME_QUERY_LIMIT, contextSuffix, regionOptions.ignoreHeadingErrors);
        time = (System.currentTimeMillis() - track);
        if (time > threshold) System.out.println("Column headings parsed in " + time + "ms");
        track = System.currentTimeMillis();
        final List<List<DataRegionHeading>> columnHeadings = DataRegionHeadingService.expandHeadings(MultidimensionalListUtils.transpose2DList(columnHeadingLists), sharedNames);
        time = (System.currentTimeMillis() - track);
        if (time > threshold) System.out.println("Column headings expanded in " + time + "ms");
        track = System.currentTimeMillis();
        if (columnHeadings.size() == 0) {
            throw new Exception("no headings passed");
        }
        if (rowHeadings.size() == 0) {
            //create a single row heading
            rowHeadings.add(new ArrayList<>());
            rowHeadings.get(0).add(new DataRegionHeading(null, false));
        }
        List<List<AzquoCell>> dataToShow = getAzquoCellsForRowsColumnsAndContext(azquoMemoryDBCOnnection, rowHeadings, columnHeadings, contextHeadings, languages, valueId, quiet);
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
        dataToShow = sortAndFilterCells(dataToShow, rowHeadings, columnHeadings
                , regionOptions, languages, permute);
        time = (System.currentTimeMillis() - track);
        if (time > threshold) System.out.println("data sort/filter in " + time + "ms");
        System.out.println("region delivered in " + (System.currentTimeMillis() - start) + "ms");
        return dataToShow;
    }

    // used by the pivot permute function, really it's building a set of shared names based on all the children of names specified in context

    static Collection<Name> getSharedNames(List<DataRegionHeading> headingList) {
        //long startTime = System.currentTimeMillis();
        Collection<Name> shared = null;
        List<Collection<Name>> relevantNameSets = new ArrayList<>();
        // gather names
        for (DataRegionHeading heading : headingList) {
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

    // for looking up a heading given a string. Used to find the col/row index to sort on (as in I want to sort on "Colour", ok what's the column number?)

    private static int findPosition(List<List<DataRegionHeading>> headings, String toFind, List<String> languages) {
        if (toFind == null || toFind.length() == 0) {
            return -1;
        }
        try {
            return Integer.parseInt(toFind);
        } catch (Exception e) {
            //so it isn't a number!
        }
        toFind = toFind.replace(" ", "").replace("`", "").toLowerCase();
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
        }
        return -1;
    }

    // note, one could derive column and row headings from the source data's headings but passing them is easier if they are to hand which the should be
    // also deals with highlighting

    private static List<List<AzquoCell>> sortAndFilterCells(List<List<AzquoCell>> sourceData, List<List<DataRegionHeading>> rowHeadings, List<List<DataRegionHeading>> columnHeadings
            , RegionOptions regionOptions, List<String> languages, boolean permute) throws Exception {
        List<List<AzquoCell>> toReturn = sourceData;
        if (sourceData == null || sourceData.isEmpty()) {
            return sourceData;
        }
        Integer sortOnColIndex = findPosition(columnHeadings, regionOptions.sortColumn, languages);
        Integer sortOnRowIndex = findPosition(rowHeadings, regionOptions.sortRow, languages); // not used at the mo
        // new logic states that sorting on row or col totals only happens if a sort row or col hasn't been passed and there are more rows or cols than max rows or cols
        int totalRows = sourceData.size();
        int totalCols = sourceData.get(0).size();

        boolean sortOnColTotals = false;
        boolean sortOnRowTotals = false;
        int maxRows = regionOptions.rowLimit;
        boolean sortColAsc = regionOptions.sortColumnAsc;
        if (maxRows != 0) {
            if (Math.abs(maxRows) < totalRows) {
                if (sortOnColIndex == -1) { // only cause total sorting if a column hasn't been passed
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

        if (sortOnColIndex != -1 || sortOnRowIndex != -1 || sortOnColTotals || sortOnRowTotals) { // then there's sorting to do
            // was a null check on sortRow and sortCol but it can't be null, it will be negative if none found
            final Map<Integer, Double> sortRowTotals = HashIntDoubleMaps.newMutableMap(sourceData.size());
            final Map<Integer, String> sortRowStrings = HashIntObjMaps.newMutableMap(sourceData.size());
            final Map<Integer, Double> sortColumnTotals = HashIntDoubleMaps.newMutableMap(totalCols);

            for (int colNo = 0; colNo < totalCols; colNo++) {
                sortColumnTotals.put(colNo, 0.00);
            }
            boolean rowNumbers = true;
            int rowNo = 0;
            // populate the maps that will be sorted
            for (List<AzquoCell> rowCells : sourceData) {
                double sortRowTotal = 0.0;//note that, if there is a 'sortCol' then only that column is added to the total. This row total or value is used when sorting by a column.
                int colNo = 0;
                sortRowStrings.put(rowNo, "");
                for (AzquoCell cell : rowCells) {
                    // ok these bits are for sorting. Could put a check on whether a number was actually the result but not so bothered
                    // info for an up/down string sort
                    if (sortOnColIndex == colNo) {// the point here is that sorting on a single column can be non numeric so we support that by jamming the string value in here
                        sortRowStrings.put(rowNo, cell.getStringValue());
                        if (cell.getStringValue().length() > 0 && !NumberUtils.isNumber(cell.getStringValue())) {
                            rowNumbers = false;
                        }
                    }
                    // info for an up/down number sort, possibly on total values of each row
                    if ((sortOnRowTotals && !DataRegionHeadingService.headingsHaveAttributes(cell.getColumnHeadings())) || sortOnColIndex == colNo) { // while running through the cells either add the lot for rowtotals or just the column we care about
                        sortRowTotal += cell.getDoubleValue();
                    }
                    // info for a left/rigt number sort possibly on column totals (left/right string sort not supported)
                    if ((sortOnColTotals && !DataRegionHeadingService.headingsHaveAttributes(cell.getRowHeadings())) || sortOnRowIndex == rowNo) {
                        sortColumnTotals.put(colNo, sortColumnTotals.get(colNo) + cell.getDoubleValue());
                    }
                    colNo++;
                }
                sortRowTotals.put(rowNo++, sortRowTotal);
            }
            List<Integer> sortedRows = new ArrayList<>();
            if (sortOnColIndex != -1 || sortOnRowTotals) { // then we need to sort the rows
                if (permute) {
                    // essentially sub sorting the last sections, will need tweaking if we want the higher levels sorted too
                    int totalHeading = rowHeadings.get(0).size() - 2;
                    int lastId = rowHeadings.get(0).get(totalHeading).getName().getId();
                    int topRow = 0;
                    for (int row = 0; row < sortRowTotals.size(); row++) {
                        int thisId = rowHeadings.get(row).get(totalHeading).getName().getId();
                        if (thisId != lastId) {
                            if (rowNumbers) {
                                Map<Integer, Double> subTotals = new HashMap<>();
                                for (int sectionRow = topRow; sectionRow < row - 1; sectionRow++) {
                                    subTotals.put(sectionRow, sortRowTotals.get(sectionRow));
                                }
                                sortedRows.addAll(sortDoubleValues(subTotals, sortColAsc));
                            } else {
                                Map<Integer, String> subTotalStrings = new HashMap<>();
                                for (int sectionRow = topRow; sectionRow < row - 1; sectionRow++) {
                                    subTotalStrings.put(sectionRow - topRow, sortRowStrings.get(sectionRow));
                                }
                                sortedRows.addAll(sortStringValues(subTotalStrings, sortColAsc));
                            }
                            sortedRows.add(row - 1);
                            topRow = row;
                            lastId = thisId;
                        }
                    }
                    sortedRows.add(sortRowTotals.size() - 1);
                } else {
                    if (rowNumbers) {
                        sortedRows = sortDoubleValues(sortRowTotals, sortColAsc);
                    } else {
                        sortedRows = sortStringValues(sortRowStrings, sortColAsc);
                    }
                }
            }

            List<Integer> sortedCols = null;
            if (sortOnRowIndex != -1 || sortOnColTotals) { // then we need to sort the cols
                sortedCols = sortDoubleValues(sortColumnTotals, regionOptions.sortRowAsc);
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
                        if (cellToCheck.getStringValue() != null && cellToCheck.getStringValue().length() > 0) {
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
                                    LocalDateTime provdate = LocalDateTime.ofInstant(value.getProvenance().getTimeStamp().toInstant(), ZoneId.systemDefault());
                                    long cellAge = provdate.until(LocalDateTime.now(), ChronoUnit.HOURS);
                                    if (cellAge < age) {
                                        age = cellAge;
                                    }
                                }
                            }
                        }
                    }
                    if (highlightHours >= age) {
                        azquoCell.setHighlighted(true);
                    }
                }
            }
        }
        return toReturn;
    }

    private static List<List<AzquoCell>> getAzquoCellsForRowsColumnsAndContext(AzquoMemoryDBConnection connection, List<List<DataRegionHeading>> headingsForEachRow
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
        if (!quiet){
            connection.addToUserLog("Size = " + totalRows + " * " + totalCols);
            connection.addToUserLog("1%--------25%---------50%---------75%--------100%");
        }
        int maxRegionSize = 2000000;//random!  set by WFC 29/6/15
        if (totalRows * totalCols > maxRegionSize) {
            throw new Exception("error: data region too large - " + totalRows + " * " + totalCols + ", max cells " + maxRegionSize);
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
        if (!quiet){
            connection.addToUserLog(" time : " + (System.currentTimeMillis() - track) + "ms");
        }
        return toReturn;
    }
}