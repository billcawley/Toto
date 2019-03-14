package com.azquo.spreadsheet;

import com.azquo.memorydb.AzquoMemoryDBConnection;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Extracted from DSSpreadsheetService by edward on 28/10/16.
 * <p>
 * Fields
 * <p>
 * The connection to the relevant DB
 * Row and column headings (very possibly more than one heading for a given row or column if permutation is involved) and context names list
 * Languages is attribute names but I think I'll call it languages as that's what it would practically be - used when looking up names for formulae
 * <p>
 * So,it's going to return relevant data to the region. The values actually shown, (typed?) objects for ZKspreadsheet, locked or not, the headings are useful (though one could perhaps derive them)
 * it seems that there should be a cell map or object and that's what this should return rather than having a bunch of multidimensional arrays
 * ok I'm going for that object type (AzquoCell), outer list rows inner items on those rows, hope that's standard. Outside this function the sorting etc will happen.
 * <p>
 * Callable interface sorts the memory "happens before" using future gets which runnable did not guarantee I don't think (though it did work).
 */
class RowFiller implements Callable<List<AzquoCell>> {
    private final int row;
    private final List<List<DataRegionHeading>> headingsForEachColumn;
    private final List<List<DataRegionHeading>> headingsForEachRow;
    private final List<DataRegionHeading> contextHeadings;
    private final List<String> languages;
    private final int valueId;
    private final AzquoMemoryDBConnection connection;
    private final AtomicInteger counter;
    private final int progressBarStep;
    private final boolean quiet;


    RowFiller(int row, List<List<DataRegionHeading>> headingsForEachColumn, List<List<DataRegionHeading>> headingsForEachRow
            , List<DataRegionHeading> contextHeadings, List<String> languages, int valueId, AzquoMemoryDBConnection connection, AtomicInteger counter, int progressBarStep, boolean quiet) {
        this.row = row;
        this.headingsForEachColumn = headingsForEachColumn;
        this.headingsForEachRow = headingsForEachRow;
        this.contextHeadings = contextHeadings;
        this.languages = languages;
        this.valueId = valueId;
        this.connection = connection;
        this.counter = counter;
        this.progressBarStep = progressBarStep;
        this.quiet = quiet;
    }

    @Override
    public List<AzquoCell> call() throws Exception {
        try {
            //System.out.println("Filling " + startRow + " to " + endRow);
            List<DataRegionHeading> rowHeadings = headingsForEachRow.get(row);
            List<AzquoCell> returnRow = new ArrayList<>(headingsForEachColumn.size());
            int colNo = 0;
            for (List<DataRegionHeading> columnHeadings : headingsForEachColumn) {
                // values I need to build the CellUI
                returnRow.add(AzquoCellResolver.getAzquoCellForHeadings(connection, rowHeadings, columnHeadings, contextHeadings, row, colNo, languages, valueId, null));
                // for some reason this was before, it buggered up the ability to find the right column!
                colNo++;
                if (!quiet && counter.incrementAndGet() % progressBarStep == 0) {
                    connection.addToUserLog("=", false);
                }
            }
            return returnRow;
        } catch (Exception e) {
            System.out.println("in row filler, tostring : " + toString());
            e.printStackTrace();
            throw e;
        }
    }

    @Override
    public String toString() {
        return "RowFiller{" +
                "row=" + row +
                ", headingsForEachColumn=" + headingsForEachColumn +
                ", headingsForEachRow=" + headingsForEachRow +
                ", contextHeadings=" + contextHeadings +
                ", languages=" + languages +
                ", valueId=" + valueId +
                ", connection=" + connection +
                ", counter=" + counter +
                ", progressBarStep=" + progressBarStep +
                ", quiet=" + quiet +
                '}';
    }
}
