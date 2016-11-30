package com.azquo.spreadsheet.view;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by edward on 24/11/16.
 *
 * A stripped down version of CellsAndHeadingsForDisplay but for Excel.
 *
 * I decided that the Excel code should not replicate CellForDisplay etc. In time there may be a better abstraction.
 */
public class CellsAndHeadingsForExcel implements Serializable{

    private final List<List<String>> columnHeadings;
    private final List<List<String>> rowHeadings;
    private final List<List<String>> data;
    private final RegionOptions options; // hybrid between DB settings and from the spreadsheet, leave it here for the mo
    private final String lockResult;

    public CellsAndHeadingsForExcel(CellsAndHeadingsForDisplay source) {
        this.columnHeadings = source.getColumnHeadings();
        this.rowHeadings = source.getRowHeadings();
        data = new ArrayList<>();
        for (List<CellForDisplay> row : source.getData()){
            List<String> dataRow = new ArrayList<>();
            data.add(dataRow);
            for (CellForDisplay cell : row){
                dataRow.add(cell.getStringValue()); // should be fine
            }
        }
        this.options = source.getOptions();
        this.lockResult = source.getLockResult();
    }

    public List<List<String>> getColumnHeadings() {
        return columnHeadings;
    }

    public List<List<String>> getRowHeadings() {
        return rowHeadings;
    }

    public List<List<String>> getData() {
        return data;
    }

    public RegionOptions getOptions() {
        return options;
    }

    public String getLockResult() {
        return lockResult;
    }
}
