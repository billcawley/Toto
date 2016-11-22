package com.azquo.spreadsheet;

import com.azquo.memorydb.AzquoMemoryDBConnection;
import com.azquo.memorydb.core.Name;
import com.azquo.memorydb.core.Value;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Extracted from DSSpreadsheetService by edward on 28/10/16.
 * More granular version of the above, less than 1000 rows, probably typical use.
 * On Damart for example we had 26*9 taking a while and it was reasonable to assume that rows were not even in terms of processing required
 */
class CellFiller implements Callable<AzquoCell> {
    private final int row;
    private final int col;
    private final List<DataRegionHeading> headingsForColumn;
    private final List<DataRegionHeading> headingsForRow;
    private final List<DataRegionHeading> contextHeadings;
    private final List<String> languages;
    private final int valueId;
    private final AzquoMemoryDBConnection connection;
    private final AtomicInteger counter;
    private final int progressBarStep;
    private final boolean quiet;

    private final Map<List<Name>, Set<Value>> nameComboValueCache;

    CellFiller(int row, int col, List<DataRegionHeading> headingsForColumn, List<DataRegionHeading> headingsForRow,
               List<DataRegionHeading> contextHeadings, List<String> languages, int valueId, AzquoMemoryDBConnection connection, AtomicInteger counter, int progressBarStep, Map<List<Name>, Set<Value>> nameComboValueCache, boolean quiet) {
        this.row = row;
        this.col = col;
        this.headingsForColumn = headingsForColumn;
        this.headingsForRow = headingsForRow;
        this.contextHeadings = contextHeadings;
        this.languages = languages;
        this.valueId = valueId;
        this.connection = connection;
        this.counter = counter;
        this.progressBarStep = progressBarStep;
        this.nameComboValueCache = nameComboValueCache;
        this.quiet = quiet;
    }

    // this should sort my memory concerns (I mean the AzquoCell being appropriately visible), call causing a memory barrier which runnable didn't.
    // Not 100% sure this error tracking is correct, leave it for the mo
    @Override
    public AzquoCell call() throws Exception {
        // connection.addToUserLog(".", false);
        try{
            final AzquoCell azquoCell = AzquoCellResolver.getAzquoCellForHeadings(connection, headingsForRow, headingsForColumn, contextHeadings, row, col, languages, valueId, nameComboValueCache);
            if (!quiet && counter.incrementAndGet() % progressBarStep == 0) {
                connection.addToUserLog("=", false);
            }
            return azquoCell;
        } catch (Exception e){
            e.printStackTrace();
            throw e;
        }
    }
}
